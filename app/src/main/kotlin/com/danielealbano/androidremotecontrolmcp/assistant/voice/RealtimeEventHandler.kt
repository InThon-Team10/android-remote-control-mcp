package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiErrorMapper
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.cancellation.CancellationException

/** What a live conversation reports to its owner. */
internal interface LiveEvents {
    fun userText(text: String)

    fun assistantText(text: String)

    fun turnEnd()

    fun speaking()

    fun listening()

    fun end(outcome: AgentOutcome)
}

/**
 * Decides when a `response.create` may go out: never while a response is active (the server rejects it), so a request
 * made meanwhile is queued until [done].
 */
internal class ResponseGate(
    private val send: (JsonObject) -> Unit,
) {
    private var active = false
    private var queued = false

    @Synchronized
    fun request() {
        if (active) {
            queued = true
        } else {
            active = true
            send(RealtimeProtocol.responseCreate())
        }
    }

    @Synchronized
    fun created() {
        active = true
    }

    @Synchronized
    fun done() {
        active = false
        if (queued) {
            queued = false
            active = true
            send(RealtimeProtocol.responseCreate())
        }
    }
}

internal fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/**
 * Handles the server events of one live conversation, in arrival order. Function calls run in [scope] and answer
 * whenever they finish, without blocking the events (a pending `wait_for_user` lets the conversation continue).
 */
internal class RealtimeEventHandler(
    private val send: (JsonObject) -> Unit,
    playback: AudioPlayback,
    guide: GuideSteps,
    listener: AgentListener,
    scope: CoroutineScope,
    private val events: LiveEvents,
) {
    private val gate = ResponseGate(send)
    private val calls = RealtimeFunctionCalls(guide, listener, scope, ::respond, ::reportFailure)
    private val audio = RealtimeAudioOutput(playback, send, events)
    private var activeResponse: String? = null
    private val handlers: Map<String, (JsonObject) -> Unit> =
        mapOf(
            "response.created" to ::onResponseCreated,
            "response.done" to ::onResponseDone,
            "response.output_audio.delta" to audio::delta,
            "response.output_audio_transcript.delta" to audio::transcript,
            "conversation.item.input_audio_transcription.completed" to ::onUserTranscript,
            "input_audio_buffer.speech_started" to { _ -> bargeIn() },
            "response.output_item.done" to ::onOutputItemDone,
            "error" to ::onError,
        )

    fun handle(event: JsonObject) {
        val type = event.string("type") ?: return
        Log.d(VOICE_TRACE, "realtime <- ${realtimeEventTrace(event)}")
        handlers[type]?.invoke(event)
    }

    /** Typed input of the user inside the conversation. */
    fun sendText(text: String) {
        events.userText(text)
        events.turnEnd()
        send(RealtimeProtocol.userText(text))
        gate.request()
    }

    private fun onResponseCreated(event: JsonObject) {
        activeResponse = event.obj("response")?.string("id")
        gate.created()
    }

    private fun onResponseDone(event: JsonObject) {
        activeResponse = null
        events.turnEnd()
        events.listening()
        val response = event.obj("response")
        if (response?.string("status") == "failed") {
            val code = response.obj("status_details")?.obj("error")?.string("code")
            events.end(AgentOutcome.Failed(OpenAiErrorMapper.mapResponseError(code)))
            return
        }
        gate.done()
    }

    private fun bargeIn() {
        audio.bargeIn(activeResponse)
        events.turnEnd()
    }

    private fun onUserTranscript(event: JsonObject) {
        event.string("transcript")?.takeIf { it.isNotBlank() }?.let(events::userText)
    }

    private fun onOutputItemDone(event: JsonObject) {
        event.obj("item")?.takeIf { it.string("type") == "function_call" }?.let(calls::start)
    }

    private fun respond(
        callId: String,
        output: String,
    ) {
        Log.d(VOICE_TRACE, "realtime -> function output (${output.length} chars)")
        send(RealtimeProtocol.functionOutput(callId, output))
        gate.request()
    }

    private fun reportFailure(error: Throwable) {
        if (error is CancellationException || error is Error) throw error
        events.end(AgentOutcome.Failed(OpenAiErrorMapper.map(error)))
    }

    private fun onError(event: JsonObject) {
        val error = event.obj("error")
        Log.d(VOICE_TRACE, "realtime error: type=${error?.string("type")} code=${error?.string("code")}")
        errorOutcome(error)?.let(events::end)
    }
}

private fun errorOutcome(error: JsonObject?): AgentOutcome? {
    val code = error?.string("code")
    return when {
        code == EXPIRED_CODE -> AgentOutcome.Completed
        code == INVALID_KEY_CODE -> AgentOutcome.Failed(AssistantError.ModelAuthFailed)
        code in QUOTA_CODES -> AgentOutcome.Failed(AssistantError.ModelQuotaExceeded)
        error?.string("type") == SERVER_ERROR_TYPE -> AgentOutcome.Failed(AssistantError.ModelUnavailable)
        else -> null
    }
}

private const val EXPIRED_CODE = "session_expired"
private const val INVALID_KEY_CODE = "invalid_api_key"
private const val SERVER_ERROR_TYPE = "server_error"
private val QUOTA_CODES = setOf("insufficient_quota", "rate_limit_exceeded")
