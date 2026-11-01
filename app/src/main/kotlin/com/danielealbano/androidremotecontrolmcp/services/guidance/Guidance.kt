package com.danielealbano.androidremotecontrolmcp.services.guidance

import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import kotlinx.coroutines.flow.StateFlow

/** What the user is asked to do on the highlighted element. */
enum class GuidanceKind { TAP, LONG_PRESS, DOUBLE_TAP, TYPE, SCROLL }

/** A point on the screen, in pixels. */
data class ScreenPoint(
    val x: Int,
    val y: Int,
)

/**
 * One guidance step shown to the user instead of acting on the device: [bounds] in screen pixels, a short
 * [instruction] (shown and spoken), an optional [explanation] of what the action will do (shown under the
 * instruction and spoken after it), the [scrollDirection] the user should swipe for [GuidanceKind.SCROLL], and for
 * a swipe gesture its [swipeStart] on screen, where the finger goes down (null for scrolling a node).
 */
data class GuidanceTarget(
    val bounds: BoundsData,
    val kind: GuidanceKind,
    val instruction: String,
    val explanation: String? = null,
    val scrollDirection: ScrollDirection? = null,
    val swipeStart: ScreenPoint? = null,
)

/** Why a [GuidanceController.guide] wait ended. */
enum class GuidanceOutcome { USER_ACTED, SCREEN_CHANGED, CANCELLED, TIMED_OUT, SUPERSEDED, UNAVAILABLE }

/** The step currently on screen; [id] is unique per step. */
data class GuidanceState(
    val id: Long,
    val target: GuidanceTarget,
)

/**
 * User activity in OTHER apps. Never carries text. The accessibility service reports the event-based kinds;
 * the overlay renderer reports [TARGET_TOUCHED] when a touch lands in the highlighted hole (apps built with
 * Compose do not send click events for touch taps, so this is the reliable tap signal).
 */
enum class UserActivity { CLICKED, LONG_CLICKED, TEXT_CHANGED, SCROLLED, WINDOW_CHANGED, TARGET_TOUCHED }

/**
 * Elder guidance: MCP tools never act on the device; they ask the user to act on a highlighted target and wait
 * for it. The overlay renders [state]; the accessibility service feeds [onUserActivity].
 */
interface GuidanceController {
    /** The step to render, or null when nothing is highlighted. */
    val state: StateFlow<GuidanceState?>

    /** Shows [target] and suspends until the user acts, the screen changes, or the step ends otherwise. */
    suspend fun guide(target: GuidanceTarget): GuidanceOutcome

    /** The user closed the guidance (bubble, close button, back). */
    fun cancel()

    /** Activity observed in another app (or a touch in the highlighted hole) while a step is shown. */
    fun onUserActivity(activity: UserActivity)

    /** The overlay renderer attached (true) or detached (false); without a renderer [guide] is UNAVAILABLE. */
    fun setRendererAttached(attached: Boolean)
}
