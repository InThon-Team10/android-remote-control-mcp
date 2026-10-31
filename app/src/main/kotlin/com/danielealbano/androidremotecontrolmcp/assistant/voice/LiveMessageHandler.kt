package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentFailure
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiErrorMapper
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiToolCallRunner
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RunContext
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.LiveServerContent
import com.google.genai.types.LiveServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.optionals.getOrNull

/** What a live conversation reports to its owner. */
internal interface LiveEvents {
    fun userText(text: String)

    fun assistantText(text: String)

    fun turnEnd()

    fun speaking()

    fun listening()

    fun end(outcome: AgentOutcome)
}

/** Runs the tool calls of the live model with the text agent's runner; the step limit counts call batches. */
internal class LiveToolRunner(
    private val runner: GeminiToolCallRunner,
    private val listener: AgentListener,
    private val context: RunContext,
) {
    private val batches = AtomicInteger()

    /** Null when the step limit is reached. */
    suspend fun run(calls: List<FunctionCall>): List<FunctionResponse>? {
        if (batches.incrementAndGet() > context.stepLimit) return null
        return calls.mapNotNull { runner.execute(it, listener, context).functionResponse().getOrNull() }
    }

    val stepLimit: Int get() = context.stepLimit
}

/** Handles the server messages of one live conversation, in arrival order. */
internal class LiveMessageHandler(
    private val connection: LiveConnection,
    private val playback: AudioPlayback,
    private val tools: LiveToolRunner,
    private val scope: CoroutineScope,
    private val events: LiveEvents,
) {
    fun handle(message: LiveServerMessage) {
        message.serverContent().getOrNull()?.let(::onContent)
        message
            .toolCall()
            .getOrNull()
            ?.functionCalls()
            ?.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::onToolCall)
        if (message.goAway().isPresent) events.end(AgentOutcome.Failed(AssistantError.GeminiUnavailable))
    }

    private fun onContent(content: LiveServerContent) {
        content.inputTranscription().getOrNull()?.text()?.getOrNull()?.let(events::userText)
        content.outputTranscription().getOrNull()?.text()?.getOrNull()?.let(events::assistantText)
        content.modelTurn().getOrNull()?.parts()?.getOrNull().orEmpty().forEach { part ->
            part.inlineData().getOrNull()?.data()?.getOrNull()?.let {
                events.speaking()
                playback.write(it)
            }
        }
        if (content.interrupted().getOrNull() == true) {
            playback.flush()
            events.turnEnd()
        }
        if (content.turnComplete().getOrNull() == true) {
            events.turnEnd()
            events.listening()
        }
    }

    private fun onToolCall(calls: List<FunctionCall>) {
        scope.launch {
            try {
                val responses = tools.run(calls)
                if (responses == null) {
                    events.end(AgentOutcome.StepLimitReached(tools.stepLimit))
                } else {
                    connection.sendToolResponse(responses)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                events.end(AgentOutcome.Failed((error as? AgentFailure)?.error ?: GeminiErrorMapper.map(error)))
            }
        }
    }
}
