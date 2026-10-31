package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What `show_next_step` tells the voice model: the sentence to say, whether a highlight is on screen, the kind. */
data class GuideStepResult(
    val say: String,
    val showing: Boolean,
    val kind: String? = null,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("say", say)
            if (kind != null) put("kind", kind)
            if (showing) put("showing", true)
        }
}

/** The two tools of the voice model: plan and show the next step, and wait for the user to act on it. */
interface GuideSteps {
    /** True while the planner works (UI "thinking" screen). */
    val planning: StateFlow<Boolean>

    /**
     * Plans the next step towards [goal] and shows it. Returns once the highlight is on screen (the user acts
     * later, see [waitForUser]) or the planner answered in words. Cancelling it clears the guidance.
     */
    suspend fun showNextStep(
        goal: String,
        listener: AgentListener,
    ): GuideStepResult

    /** Suspends until the pending highlight ends; the result is a short English text for the voice model. */
    suspend fun waitForUser(): String

    /** Drops the step history of the session and clears the guidance. */
    fun reset()
}
