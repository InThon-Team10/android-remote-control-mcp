package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentResponse
import kotlin.jvm.optionals.getOrNull

/** Extracts the model turn from a response or throws the matching [AgentFailure]. */
internal object GeminiResponseReader {
    private val BLOCKING_FINISH_REASONS =
        setOf(
            FinishReason.Known.SAFETY,
            FinishReason.Known.RECITATION,
            FinishReason.Known.BLOCKLIST,
            FinishReason.Known.PROHIBITED_CONTENT,
            FinishReason.Known.SPII,
            FinishReason.Known.IMAGE_SAFETY,
            FinishReason.Known.LANGUAGE,
        )

    /** The model's [Content], to be appended to the history verbatim (keeps thought signatures). */
    fun modelContent(response: GenerateContentResponse): Content {
        checkNotBlocked(response)
        val candidate =
            response.candidates().getOrNull()?.firstOrNull()
                ?: throw AgentFailure(AssistantError.GeminiRequestFailed("No candidate in the response."))
        val content = candidate.content().getOrNull()
        if (content == null || content.parts().getOrNull().isNullOrEmpty()) {
            val reason = candidate.finishReason().getOrNull()?.knownEnum()
            throw AgentFailure(
                if (reason in BLOCKING_FINISH_REASONS) {
                    AssistantError.GeminiBlocked(reason.toString())
                } else {
                    AssistantError.GeminiRequestFailed("Empty response (finish reason: $reason).")
                },
            )
        }
        return content
    }

    private fun checkNotBlocked(response: GenerateContentResponse) {
        response
            .promptFeedback()
            .getOrNull()
            ?.blockReason()
            ?.getOrNull()
            ?.let { throw AgentFailure(AssistantError.GeminiBlocked(it.toString())) }
    }

    fun texts(content: Content): List<String> =
        content
            .parts()
            .getOrNull()
            .orEmpty()
            .filter { it.thought().getOrNull() != true }
            .mapNotNull { it.text().getOrNull() }
            .filter { it.isNotBlank() }

    fun functionCalls(content: Content): List<FunctionCall> =
        content
            .parts()
            .getOrNull()
            .orEmpty()
            .mapNotNull { it.functionCall().getOrNull() }
}
