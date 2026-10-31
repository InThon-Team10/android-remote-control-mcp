package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Runs the function calls of the voice model (`show_next_step`, `wait_for_user`) in [scope]; each answers through
 * [respond] whenever it finishes, so a pending `wait_for_user` never blocks the conversation. [fail] gets any failure.
 */
internal class RealtimeFunctionCalls(
    private val guide: GuideSteps,
    private val listener: AgentListener,
    private val scope: CoroutineScope,
    private val respond: (callId: String, output: String) -> Unit,
    private val fail: (Throwable) -> Unit,
) {
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
            RealtimeProtocol.WAIT_FOR_USER -> guide.waitForUser()
            else -> "Unknown tool: $name"
        }

    private suspend fun showNextStep(arguments: String): String {
        val goal =
            runCatching { Json.parseToJsonElement(arguments).jsonObject.string("goal") }.getOrNull()?.takeIf {
                it.isNotBlank()
            } ?: return "The goal is missing: call show_next_step again with the goal."
        return guide.showNextStep(goal, listener).toJson().toString()
    }
}
