package com.danielealbano.androidremotecontrolmcp.services.guidance

/**
 * The decision logic of one shown guidance step: which [UserActivity] counts for its [GuidanceKind], how long the
 * step waits after it (settle time) before it is [GuidanceOutcome.USER_ACTED], and when a window change ends it.
 * Pure: the caller supplies the current time, and acts on [remainingMs].
 */
internal class StepTracker(
    private val kind: GuidanceKind,
    private val shownAtMs: Long,
) {
    /** True once an activity that counts for [kind] was seen (also used when the wait cap hits). */
    var activitySeen = false
        private set

    private var completeAtMs: Long? = null

    /** Milliseconds until the step completes as USER_ACTED when nothing else happens, or null if none is pending. */
    fun remainingMs(nowMs: Long): Long? = completeAtMs?.let { (it - nowMs).coerceAtLeast(0L) }

    /** The outcome when the wait cap hits: the user was acting (e.g. still typing) or did not act. */
    fun outcomeAtCap(): GuidanceOutcome = if (activitySeen) GuidanceOutcome.USER_ACTED else GuidanceOutcome.TIMED_OUT

    /** Feeds [activity]; returns the outcome when it decides the step now, null otherwise. */
    fun onActivity(
        activity: UserActivity,
        nowMs: Long,
    ): GuidanceOutcome? =
        when {
            activity == UserActivity.WINDOW_CHANGED -> onWindowChanged(nowMs)
            !GuidanceActivityRules.qualifies(kind, activity) -> null
            else -> onQualifying(activity, nowMs)
        }

    private fun onWindowChanged(nowMs: Long): GuidanceOutcome? =
        when {
            activitySeen -> GuidanceOutcome.USER_ACTED
            nowMs - shownAtMs < GuidanceActivityRules.WINDOW_GRACE_MS -> null
            else -> GuidanceOutcome.SCREEN_CHANGED
        }

    private fun onQualifying(
        activity: UserActivity,
        nowMs: Long,
    ): GuidanceOutcome? {
        activitySeen = true
        return when (kind) {
            GuidanceKind.TAP -> {
                settleOnce(nowMs, GuidanceActivityRules.TAP_SETTLE_MS)
            }

            GuidanceKind.DOUBLE_TAP -> {
                settleOnce(nowMs, GuidanceActivityRules.DOUBLE_TAP_SETTLE_MS)
            }

            GuidanceKind.LONG_PRESS -> {
                if (activity == UserActivity.LONG_CLICKED) {
                    GuidanceOutcome.USER_ACTED
                } else {
                    settleOnce(nowMs, GuidanceActivityRules.LONG_PRESS_SETTLE_MS)
                }
            }

            GuidanceKind.TYPE -> {
                restart(nowMs, GuidanceActivityRules.TYPE_QUIET_MS)
            }

            GuidanceKind.SCROLL -> {
                val quiet =
                    if (activity == UserActivity.TARGET_TOUCHED) {
                        GuidanceActivityRules.SWIPE_SETTLE_MS
                    } else {
                        GuidanceActivityRules.SCROLL_QUIET_MS
                    }
                restart(nowMs, quiet)
            }
        }
    }

    /** Starts the settle timer unless one already runs (further taps do not postpone it). */
    private fun settleOnce(
        nowMs: Long,
        settleMs: Long,
    ): GuidanceOutcome? {
        if (completeAtMs == null) completeAtMs = nowMs + settleMs
        return null
    }

    /** (Re)starts the quiet timer: every new activity postpones the completion. */
    private fun restart(
        nowMs: Long,
        quietMs: Long,
    ): GuidanceOutcome? {
        completeAtMs = nowMs + quietMs
        return null
    }
}

/** Which activity counts for which step kind, and the timings of the rules. */
internal object GuidanceActivityRules {
    const val TAP_SETTLE_MS = 300L
    const val DOUBLE_TAP_SETTLE_MS = 800L
    const val LONG_PRESS_SETTLE_MS = 1_500L
    const val TYPE_QUIET_MS = 2_500L
    const val SCROLL_QUIET_MS = 600L

    /** After a touch in the highlighted area of a swipe/scroll step: room for the gesture and its animation. */
    const val SWIPE_SETTLE_MS = 1_200L
    const val WINDOW_GRACE_MS = 700L

    /** True when [activity] is the user doing what [kind] asks (window changes are handled separately). */
    fun qualifies(
        kind: GuidanceKind,
        activity: UserActivity,
    ): Boolean =
        when (kind) {
            GuidanceKind.TAP, GuidanceKind.DOUBLE_TAP -> {
                activity == UserActivity.CLICKED || activity == UserActivity.TARGET_TOUCHED
            }

            GuidanceKind.LONG_PRESS -> {
                activity == UserActivity.LONG_CLICKED ||
                    activity == UserActivity.CLICKED ||
                    activity == UserActivity.TARGET_TOUCHED
            }

            GuidanceKind.TYPE -> {
                activity == UserActivity.TEXT_CHANGED
            }

            GuidanceKind.SCROLL -> {
                // Launchers report no scroll event for the swipe that opens the app list: the touch counts too.
                activity == UserActivity.SCROLLED || activity == UserActivity.TARGET_TOUCHED
            }
        }
}
