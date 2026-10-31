package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.openai.models.responses.FunctionTool
import kotlin.coroutines.cancellation.CancellationException

/** Aborts a run with a typed [error]; converted to `AgentOutcome.Failed` by the agent. */
internal class AgentFailure(
    val error: AssistantError,
) : Exception(error.toString())

/** Everything fixed for the duration of one OpenAI run. Deliberately not a data class (no key in `toString`). */
internal class OpenAiRunContext(
    val apiKey: String,
    val model: String,
    val tools: List<FunctionTool>,
    val stepLimit: Int,
    val deviceSlug: String,
    val toolNames: Set<String>,
)

/** MCP-side failure to [AgentFailure]; cancellation is rethrown untouched. */
internal fun Throwable.toMcpFailure(): AgentFailure {
    if (this is CancellationException) throw this
    val mapped = (this as? AssistantException)?.error
    return AgentFailure(mapped ?: AssistantError.McpUnavailable(javaClass.simpleName))
}
