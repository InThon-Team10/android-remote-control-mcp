package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentFailure
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiErrorMapper
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiToolCallRunner
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RunContext
import com.danielealbano.androidremotecontrolmcp.mcp.tools.ElderToolGuidance
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.LiveServerContent
import com.google.genai.types.LiveServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
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
    private var batches = 0

    /** Null when the step limit is reached. */
    suspend fun run(calls: List<FunctionCall>): List<FunctionResponse>? {
        if (++batches > context.stepLimit) return null
        return calls
            .mapNotNull { runner.execute(it, listener, context).functionResponse().getOrNull() }
            .filterNot(::isSupersededHighlight)
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
        Log.d(VOICE_TRACE, "live <- ${liveMessageTrace(message)}")
        message.serverContent().getOrNull()?.let(::onContent)
        message
            .toolCall()
            .getOrNull()
            ?.functionCalls()
            ?.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::onToolCall)
        message
            .toolCallCancellation()
            .getOrNull()
            ?.ids()
            ?.getOrNull()
            ?.let(::onCancellation)
        if (message.goAway().isPresent) events.end(AgentOutcome.Failed(AssistantError.GeminiUnavailable))
    }

    private fun onContent(content: LiveServerContent) {
        content
            .inputTranscription()
            .getOrNull()
            ?.text()
            ?.getOrNull()
            ?.let(events::userText)
        content
            .outputTranscription()
            .getOrNull()
            ?.text()
            ?.getOrNull()
            ?.let(events::assistantText)
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

    private val calls = ConcurrentHashMap<String, Job>()

    private fun onToolCall(functionCalls: List<FunctionCall>) {
        val ids = functionCalls.mapNotNull { it.id().getOrNull() }
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    runCatching { respond(tools.run(functionCalls)) }.exceptionOrNull()?.let(::reportFailure)
                } finally {
                    ids.forEach(calls::remove)
                }
            }
        ids.forEach { calls[it] = job }
        job.start()
    }

    private suspend fun respond(responses: List<FunctionResponse>?) {
        if (responses == null) {
            events.end(AgentOutcome.StepLimitReached(tools.stepLimit))
        } else if (responses.isNotEmpty()) {
            Log.d(VOICE_TRACE, "live -> toolResponse x${responses.size}")
            connection.sendToolResponse(responses)
        }
    }

    private fun reportFailure(error: Throwable) {
        if (error is CancellationException || error is Error) throw error
        events.end(AgentOutcome.Failed((error as? AgentFailure)?.error ?: GeminiErrorMapper.map(error)))
    }

    private fun onCancellation(ids: List<String>) {
        val running = ids.count { calls.containsKey(it) }
        Log.d(VOICE_TRACE, "live tool calls cancelled by the server: $running/${ids.size} running")
        ids.forEach { calls.remove(it)?.cancel() }
    }
}

/**
 * A highlight replaced by the live model's own newer highlight call. Answering it would start a new model turn that
 * issues yet another highlight (a loop that never lets the user act), so it is left unanswered, like a call the
 * server cancelled.
 */
internal fun isSupersededHighlight(response: FunctionResponse): Boolean =
    response.response().getOrNull()?.get("error") == ElderToolGuidance.SUPERSEDED_TEXT
