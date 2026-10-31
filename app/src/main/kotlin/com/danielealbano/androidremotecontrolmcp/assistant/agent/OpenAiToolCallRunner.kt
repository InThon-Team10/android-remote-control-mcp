package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes ONE Responses API function call: unknown-tool and argument guards, confirmation (D10), listener callbacks,
 * MCP call. The answer is always a `function_call_output` item for the call's `call_id`.
 */
@Singleton
class OpenAiToolCallRunner
    @Inject
    constructor(
        private val connection: AssistantMcpConnection,
    ) {
        private val callCounter = AtomicLong()

        internal suspend fun execute(
            call: ResponseFunctionToolCall,
            listener: AgentListener,
            context: OpenAiRunContext,
        ): ResponseInputItem {
            val name = call.name()
            val arguments = parseArguments(call.arguments())
            val callId = "call-${callCounter.incrementAndGet()}"
            return when {
                name !in context.toolNames -> {
                    OpenAiToolOutput.unknownTool(call.callId())
                }

                arguments == null -> {
                    OpenAiToolOutput.invalidArguments(call.callId())
                }

                ConfirmationPolicy.requiresConfirmation(name, context.deviceSlug) &&
                    !listener.confirm(callId, name) -> {
                    OpenAiToolOutput.denied(call.callId())
                }

                else -> {
                    invoke(call, arguments, callId, listener, context.deviceSlug)
                }
            }
        }

        private suspend fun invoke(
            call: ResponseFunctionToolCall,
            arguments: JsonObject,
            callId: String,
            listener: AgentListener,
            deviceSlug: String,
        ): ResponseInputItem {
            val name = call.name()
            listener.beforeToolCall()
            listener.onToolCallStarted(callId, name)
            val result = connection.callTool(name, ElderToolFilter.withDefaults(name, arguments, deviceSlug))
            listener.onToolCallFinished(callId, name, result.fold({ it.isError == true }, { true }))
            return OpenAiToolOutput.fromResult(call.callId(), result.getOrElse { throw it.toMcpFailure() })
        }

        /** The model sends the arguments as JSON text; blank means none. Null when it is not a JSON object. */
        private fun parseArguments(arguments: String): JsonObject? =
            runCatching { Json.parseToJsonElement(arguments.ifBlank { EMPTY_ARGUMENTS }) as? JsonObject }.getOrNull()

        private companion object {
            const val EMPTY_ARGUMENTS = "{}"
        }
    }
