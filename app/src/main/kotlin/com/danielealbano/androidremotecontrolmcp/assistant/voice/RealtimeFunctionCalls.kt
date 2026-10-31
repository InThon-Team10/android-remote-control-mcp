package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideOutcomeTexts
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the function calls of the voice model (`show_next_step`, `wait_for_user`) in [scope]; each answers through
 * [respond] whenever it finishes, so a pending `wait_for_user` never blocks the conversation. [fail] gets any failure.
 *
 * When the user completes a step, `wait_for_user` plans and shows the next step towards the current goal itself and
 * answers with both: on the phone the voice model often said "let's go to the next step" and then ended its turn
 * without calling `show_next_step`, leaving the user in silence.
 */
internal class RealtimeFunctionCalls(
    private val guide: GuideSteps,
    private val listener: AgentListener,
    private val scope: CoroutineScope,
    private val respond: (callId: String, output: String) -> Unit,
    private val fail: (Throwable) -> Unit,
) {
    private val goal = AtomicReference<String?>(null)

    /** [item] is a `function_call` output item; one without a call id is ignored. */
    fun start(item: JsonObject) {
        val callId = item.string("call_id") ?: return
        val name = item.string("name").orEmpty()
        val arguments = item.string("arguments").orEmpty()
        scope.launch {
            val result = runCatching { execute(name, arguments) }
            val failure = result.exceptionOrNull()
            if (failure == null) respond(callId, result.getOrThrow()) else fail(failure)
        }
    }

    private suspend fun execute(
        name: String,
        arguments: String,
    ): String =
        when (name) {
            RealtimeProtocol.SHOW_NEXT_STEP -> showNextStep(arguments)
            RealtimeProtocol.WAIT_FOR_USER -> waitForUser()
            else -> "Unknown tool: $name"
        }

    private suspend fun showNextStep(arguments: String): String {
        val goal =
            runCatching { Json.parseToJsonElement(arguments).jsonObject.string("goal") }.getOrNull()?.takeIf {
                it.isNotBlank()
            } ?: return "The goal is missing: call show_next_step again with the goal."
        this.goal.set(goal)
        val result = guide.showNextStep(goal, listener)
        if (result.kind == DONE) this.goal.compareAndSet(goal, null)
        return result.toJson().toString()
    }

    private suspend fun waitForUser(): String {
        val outcome = guide.waitForUser()
        val current = goal.get()
        if (current == null || outcome !in CONTINUING_OUTCOMES) return outcome
        val next = guide.showNextStep(current, listener)
        if (next.kind == DONE) goal.compareAndSet(current, null)
        return buildJsonObject {
            put("result", outcome)
            put("next", next.toJson())
        }.toString()
    }

    private companion object {
        const val DONE = "done"
        val CONTINUING_OUTCOMES = setOf(GuideOutcomeTexts.USER_DID_IT, GuideOutcomeTexts.SCREEN_CHANGED)
    }
}
