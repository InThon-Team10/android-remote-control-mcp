package com.danielealbano.androidremotecontrolmcp.services.guidance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class HighlightBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0 && top >= 0 && right > left && bottom > top) { "Invalid highlight bounds" }
    }
}

@Serializable
enum class GuidancePhase { IDLE, NEEDS_ANALYSIS, WAITING_USER, COMPLETED, CANCELLED, DISCONNECTED, ERROR }

@Serializable
data class GuidanceState(
    val goal: String = "",
    val revision: Long = 0,
    val phase: GuidancePhase = GuidancePhase.IDLE,
    val instruction: String = "",
    val bounds: HighlightBounds? = null,
    val step: Int = 0,
)

/** UI and agent adapters share this state machine. Screen changes request analysis, never imply success. */
class GuidanceSession {
    private val mutableState = MutableStateFlow(GuidanceState())
    val state = mutableState.asStateFlow()

    @Synchronized
    fun start(goal: String = TEST_GOAL): GuidanceState {
        require(goal.isNotBlank() && goal.length <= MAX_TEXT_LENGTH) { "Goal must contain 1..2000 characters" }
        val old = state.value
        check(old.phase != GuidancePhase.WAITING_USER && old.phase != GuidancePhase.NEEDS_ANALYSIS) {
            "A guidance session is already active; cancel it before starting another"
        }
        return publish(GuidanceState(goal = goal, revision = old.revision + 1, phase = GuidancePhase.NEEDS_ANALYSIS))
    }

    @Synchronized
    fun propose(
        revision: Long,
        instruction: String,
        bounds: HighlightBounds,
    ): GuidanceState {
        val old = state.value
        requireCurrent(revision)
        require(instruction.isNotBlank() && instruction.length <= MAX_TEXT_LENGTH) {
            "Instruction must contain 1..2000 characters"
        }
        if (old.phase == GuidancePhase.WAITING_USER && old.instruction == instruction && old.bounds == bounds) {
            return old
        }
        check(old.phase == GuidancePhase.NEEDS_ANALYSIS) { "Wait for the user before proposing another step" }
        return publish(
            old.copy(
                phase = GuidancePhase.WAITING_USER,
                instruction = instruction,
                bounds = bounds,
                step = old.step + 1,
            ),
        )
    }

    @Synchronized
    fun screenChanged(): GuidanceState {
        val old = state.value
        val active = old.phase == GuidancePhase.WAITING_USER || old.phase == GuidancePhase.NEEDS_ANALYSIS
        return publish(
            old.copy(
                revision = old.revision + 1,
                phase = if (active) GuidancePhase.NEEDS_ANALYSIS else old.phase,
                bounds = null,
            ),
        )
    }

    @Synchronized
    fun complete(revision: Long): GuidanceState {
        requireCurrent(revision)
        check(state.value.phase == GuidancePhase.NEEDS_ANALYSIS) {
            "Read and verify the resulting screen before completing"
        }
        return publish(state.value.copy(phase = GuidancePhase.COMPLETED, bounds = null))
    }

    @Synchronized
    fun cancel(): GuidanceState = publish(state.value.copy(phase = GuidancePhase.CANCELLED, bounds = null))

    @Synchronized
    fun fail(instruction: String): GuidanceState =
        publish(
            state.value.copy(
                phase = GuidancePhase.ERROR,
                bounds = null,
                instruction = instruction.take(MAX_TEXT_LENGTH),
            ),
        )

    @Synchronized
    fun disconnect(): GuidanceState =
        publish(
            state.value.copy(
                phase = GuidancePhase.DISCONNECTED,
                bounds = null,
                revision =
                    state.value.revision + 1,
            ),
        )

    private fun requireCurrent(revision: Long) {
        check(revision == state.value.revision) { "Screen changed; read guidance_status and analyze the screen again" }
    }

    private fun publish(value: GuidanceState): GuidanceState {
        mutableState.value = value
        return value
    }

    companion object {
        const val TEST_GOAL = "카카오톡 켜는법"
        private const val MAX_TEXT_LENGTH = 2000
    }
}
