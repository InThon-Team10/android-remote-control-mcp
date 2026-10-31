package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.types.BlockedReason
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponsePromptFeedback
import com.google.genai.types.Part

/** Scripted [GeminiModelGateway]: answers are consumed in order; every call is recorded (history is copied). */
class FakeGeminiModelGateway : GeminiModelGateway {
    class Call(
        val apiKey: String,
        val model: String,
        val history: List<Content>,
        val config: GenerateContentConfig,
    )

    val calls = mutableListOf<Call>()
    private val script = ArrayDeque<() -> GenerateContentResponse>()

    fun enqueue(response: GenerateContentResponse) = script.addLast { response }

    fun enqueueFailure(error: Throwable) = script.addLast { throw error }

    override suspend fun generate(
        apiKey: String,
        model: String,
        history: List<Content>,
        config: GenerateContentConfig,
    ): GenerateContentResponse {
        calls += Call(apiKey, model, history.toList(), config)
        return checkNotNull(script.removeFirstOrNull()) { "No scripted Gemini response left" }.invoke()
    }
}

object GeminiTestResponses {
    fun response(
        content: Content?,
        finish: FinishReason.Known = FinishReason.Known.STOP,
    ): GenerateContentResponse {
        val candidate = Candidate.builder().finishReason(finish)
        content?.let { candidate.content(it) }
        return GenerateContentResponse.builder().candidates(listOf(candidate.build())).build()
    }

    fun text(text: String): GenerateContentResponse =
        response(
            Content
                .builder()
                .role("model")
                .parts(listOf(Part.fromText(text)))
                .build(),
        )

    /** One model turn with the given parallel calls; [signature] is attached to the first call part. */
    fun calls(
        vararg calls: Pair<String, Map<String, Any>>,
        signature: ByteArray? = null,
    ): GenerateContentResponse {
        val parts =
            calls.mapIndexed { index, (name, args) ->
                val call =
                    FunctionCall
                        .builder()
                        .name(name)
                        .args(args)
                        .build()
                val builder = Part.builder().functionCall(call)
                if (index == 0 && signature != null) builder.thoughtSignature(signature)
                builder.build()
            }
        return response(
            Content
                .builder()
                .role("model")
                .parts(parts)
                .build(),
        )
    }

    fun promptBlocked(reason: BlockedReason.Known): GenerateContentResponse =
        GenerateContentResponse
            .builder()
            .promptFeedback(GenerateContentResponsePromptFeedback.builder().blockReason(reason).build())
            .build()
}

/** Records every [AgentListener] callback in order; [confirmAnswer] decides confirmations. */
class RecordingAgentListener(
    var confirmAnswer: (toolName: String) -> Boolean = { true },
) : AgentListener {
    val events = mutableListOf<String>()

    override fun onAssistantText(text: String) {
        events += "text:$text"
    }

    override suspend fun beforeToolCall() {
        events += "before"
    }

    override fun onToolCallStarted(
        callId: String,
        toolName: String,
    ) {
        events += "started:$toolName"
    }

    override fun onToolCallFinished(
        callId: String,
        toolName: String,
        isError: Boolean,
    ) {
        events += "finished:$toolName:$isError"
    }

    override suspend fun confirm(
        callId: String,
        toolName: String,
    ): Boolean {
        events += "confirm:$toolName"
        return confirmAnswer(toolName)
    }
}
