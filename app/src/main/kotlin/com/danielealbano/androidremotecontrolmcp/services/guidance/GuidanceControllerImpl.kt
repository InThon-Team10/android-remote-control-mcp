package com.danielealbano.androidremotecontrolmcp.services.guidance

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [GuidanceController] with the elder-guidance rules: one step at a time, waiting for the user's own action.
 *
 * Every timer runs as a coroutine delay inside [guide] (activity reaches it through a channel), so nothing blocks
 * the calling thread and test dispatchers drive the timing. State changes are made under [lock]; the one place that
 * outlives [guide], the lingering highlight, expires through [scope].
 */
@Singleton
class GuidanceControllerImpl internal constructor(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
) : GuidanceController {
    @Inject
    constructor() : this(CoroutineScope(SupervisorJob() + Dispatchers.Default), { System.nanoTime() / NANOS_PER_MS })

    private sealed interface Signal {
        data class Activity(
            val activity: UserActivity,
        ) : Signal

        data class End(
            val outcome: GuidanceOutcome,
        ) : Signal
    }

    private class Shown(
        val state: GuidanceState,
        val shownAtMs: Long,
    )

    private class Step(
        val shown: Shown,
        val tracker: StepTracker,
    ) {
        val signals = Channel<Signal>(Channel.UNLIMITED)
    }

    private class Lingering(
        val shown: Shown,
        var expiry: Job? = null,
    )

    private val lock = Any()
    private val _state = MutableStateFlow<GuidanceState?>(null)
    override val state: StateFlow<GuidanceState?> = _state.asStateFlow()

    private var rendererAttached = false
    private var nextId = 0L
    private var current: Step? = null
    private var lingering: Lingering? = null
    private var actedTarget: GuidanceTarget? = null

    override suspend fun guide(target: GuidanceTarget): GuidanceOutcome {
        val step =
            when (val begun = begin(target)) {
                is Begun.Immediate -> return begun.outcome
                is Begun.Waiting -> begun.step
            }
        var outcome: GuidanceOutcome? = null
        Log.d("MCP:VoiceTrace", "guidance step waiting kind=${target.kind}")
        try {
            outcome = withTimeoutOrNull(GUIDANCE_WAIT_MS) { listen(step) } ?: step.tracker.outcomeAtCap()
            Log.d("MCP:VoiceTrace", "guidance step ended: $outcome")
            return outcome
        } finally {
            finish(step, outcome)
        }
    }

    override fun cancel() {
        synchronized(lock) {
            current?.signals?.trySend(Signal.End(GuidanceOutcome.CANCELLED))
            clearAll()
        }
    }

    override fun onUserActivity(activity: UserActivity) {
        synchronized(lock) {
            val step = current
            val linger = lingering
            when {
                step != null -> step.signals.trySend(Signal.Activity(activity))
                linger != null -> onLingeringActivity(linger, activity)
            }
        }
    }

    override fun setRendererAttached(attached: Boolean) {
        synchronized(lock) {
            rendererAttached = attached
            if (!attached) {
                current?.signals?.trySend(Signal.End(GuidanceOutcome.UNAVAILABLE))
                clearAll()
            }
        }
    }

    private sealed interface Begun {
        data class Immediate(
            val outcome: GuidanceOutcome,
        ) : Begun

        data class Waiting(
            val step: Step,
        ) : Begun
    }

    private fun begin(target: GuidanceTarget): Begun =
        synchronized(lock) {
            when {
                !rendererAttached -> {
                    Begun.Immediate(GuidanceOutcome.UNAVAILABLE)
                }

                target == actedTarget -> {
                    actedTarget = null
                    Begun.Immediate(GuidanceOutcome.USER_ACTED)
                }

                else -> {
                    Begun.Waiting(show(target))
                }
            }
        }

    /** Replaces whatever is shown with [target], keeping the state id (and so the renderer's phase) if unchanged. */
    private fun show(target: GuidanceTarget): Step {
        val previous = current?.shown ?: lingering?.shown
        current?.signals?.trySend(Signal.End(GuidanceOutcome.SUPERSEDED))
        lingering?.expiry?.cancel()
        lingering = null
        actedTarget = null
        val shown =
            if (previous != null && previous.state.target == target) {
                previous
            } else {
                Shown(GuidanceState(++nextId, target), clock()).also { _state.value = it.state }
            }
        return Step(shown, StepTracker(target.kind, shown.shownAtMs)).also { current = it }
    }

    /** Feeds the step's signals to its tracker until one decides the outcome (or the settle time elapses). */
    private suspend fun listen(step: Step): GuidanceOutcome {
        while (true) {
            val signal = receive(step) ?: return GuidanceOutcome.USER_ACTED
            val decided =
                when (signal) {
                    is Signal.End -> signal.outcome
                    is Signal.Activity -> step.tracker.onActivity(signal.activity, clock())
                }
            if (decided != null) return decided
        }
    }

    /** The next signal, or null when the step's settle time elapses first. */
    private suspend fun receive(step: Step): Signal? {
        val settleMs = step.tracker.remainingMs(clock()) ?: return step.signals.receive()
        return withTimeoutOrNull(settleMs) { step.signals.receive() }
    }

    /** Ends [step]; [outcome] is null when the caller was cancelled. A timed-out step stays shown (lingering). */
    private fun finish(
        step: Step,
        outcome: GuidanceOutcome?,
    ) {
        synchronized(lock) {
            if (current !== step) return
            current = null
            if (outcome == GuidanceOutcome.TIMED_OUT) {
                lingering =
                    Lingering(step.shown).also { linger ->
                        linger.expiry =
                            scope.launch {
                                delay(LINGER_MS)
                                synchronized(lock) { if (lingering === linger) clearAll() }
                            }
                    }
            } else {
                _state.value = null
            }
        }
        if (outcome == null) Log.d(TAG, "Guidance wait cancelled by the caller")
    }

    private fun onLingeringActivity(
        linger: Lingering,
        activity: UserActivity,
    ) {
        val target = linger.shown.state.target
        val acted = GuidanceActivityRules.qualifies(target.kind, activity)
        if (acted || activity == UserActivity.WINDOW_CHANGED) {
            clearAll()
            if (acted) actedTarget = target
        }
    }

    /** Hides the step: no current step, no lingering one, nothing remembered. */
    private fun clearAll() {
        current = null
        lingering?.expiry?.cancel()
        lingering = null
        actedTarget = null
        _state.value = null
    }

    companion object {
        /** How long [guide] waits for the user; the MCP client's request timeout is 60 s. */
        const val GUIDANCE_WAIT_MS = 45_000L

        /** How long a timed-out highlight stays on screen. */
        const val LINGER_MS = 120_000L

        private const val NANOS_PER_MS = 1_000_000L
        private const val TAG = "MCP:GuidanceController"
    }
}
