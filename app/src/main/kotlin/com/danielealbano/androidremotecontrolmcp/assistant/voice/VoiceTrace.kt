package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.util.Log
import com.google.genai.types.LiveServerMessage
import kotlin.jvm.optionals.getOrNull

/** Tag shared by the content-free voice traces (event types, sizes and timings only; never transcript text). */
internal const val VOICE_TRACE = "MCP:VoiceTrace"

/** One-line description of a live server message for traces: sizes and flags, never text. */
internal fun liveMessageTrace(message: LiveServerMessage): String {
    val parts = mutableListOf<String>()
    message.serverContent().getOrNull()?.let { content ->
        content
            .inputTranscription()
            .getOrNull()
            ?.text()
            ?.getOrNull()
            ?.let { parts += "in=${it.length}" }
        content
            .outputTranscription()
            .getOrNull()
            ?.text()
            ?.getOrNull()
            ?.let { parts += "out=${it.length}" }
        val audio =
            content.modelTurn().getOrNull()?.parts()?.getOrNull().orEmpty().sumOf { part ->
                part
                    .inlineData()
                    .getOrNull()
                    ?.data()
                    ?.getOrNull()
                    ?.size ?: 0
            }
        if (audio > 0) parts += "audio=${audio}B"
        if (content.interrupted().getOrNull() == true) parts += "INTERRUPTED"
        if (content.turnComplete().getOrNull() == true) parts += "turnComplete"
    }
    message.toolCall().getOrNull()?.functionCalls()?.getOrNull()?.let { calls ->
        parts += "toolCall=" + calls.joinToString(",") { "${it.name().getOrNull()}#${it.id().getOrNull()}" }
    }
    message
        .toolCallCancellation()
        .getOrNull()
        ?.ids()
        ?.getOrNull()
        ?.let { parts += "cancel=$it" }
    if (message.goAway().isPresent) parts += "goAway"
    return parts.joinToString(" ").ifEmpty { "other" }
}

/** Counts microphone chunks and send latency; logs a summary every [EVERY] chunks. */
internal class MicTrace {
    private var chunks = 0
    private var bytes = 0L
    private var maxMs = 0L
    private var totalMs = 0L

    suspend fun timed(
        size: Int,
        send: suspend () -> Unit,
    ) {
        val start = System.nanoTime()
        send()
        val ms = (System.nanoTime() - start) / NANOS_PER_MS
        chunks++
        bytes += size
        totalMs += ms
        if (ms > maxMs) maxMs = ms
        if (chunks % EVERY == 0) {
            val average = totalMs / EVERY
            Log.d(VOICE_TRACE, "mic sent $chunks chunks ${bytes}B; last $EVERY: max ${maxMs}ms avg ${average}ms")
            maxMs = 0
            totalMs = 0
        }
    }

    private companion object {
        const val EVERY = 50
        const val NANOS_PER_MS = 1_000_000L
    }
}
