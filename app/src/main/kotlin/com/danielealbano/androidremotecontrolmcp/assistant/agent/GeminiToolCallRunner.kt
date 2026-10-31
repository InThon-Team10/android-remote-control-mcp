package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.google.genai.types.FunctionCall
import com.google.genai.types.Part
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.optionals.getOrNull

/** Executes ONE Gemini function call: unknown-tool guard, confirmation (D10), listener callbacks, MCP call. */
@Singleton
class GeminiToolCallRunner
    @Inject
    constructor(
        private val connection: AssistantMcpConnection,
    ) {
        private val callCounter = AtomicLong()

        internal suspend fun execute(
            call: FunctionCall,
            listener: AgentListener,
            context: RunContext,
        ): Part {
            val name = call.name().getOrNull().orEmpty()
            val callId = "call-${callCounter.incrementAndGet()}"
            return when {
                name !in context.toolNames -> ToolResultMapper.unknownTool(call)

                ConfirmationPolicy.requiresConfirmation(name, context.deviceSlug) &&
                    !listener.confirm(callId, name) -> ToolResultMapper.denied(call)

                else -> invoke(call, name, callId, listener, context.deviceSlug)
            }
        }

        private suspend fun invoke(
            call: FunctionCall,
            name: String,
            callId: String,
            listener: AgentListener,
            deviceSlug: String,
        ): Part {
            listener.beforeToolCall()
            listener.onToolCallStarted(callId, name)
            val arguments =
                ElderToolFilter.withDefaults(
                    name,
                    JsonBridge.toJsonObject(call.args().getOrNull().orEmpty()),
                    deviceSlug,
                )
            val result = connection.callTool(name, arguments)
            listener.onToolCallFinished(callId, name, result.fold({ it.isError == true }, { true }))
            return ToolResultMapper.fromResult(call, result.getOrElse { throw it.toMcpFailure() })
        }
    }
