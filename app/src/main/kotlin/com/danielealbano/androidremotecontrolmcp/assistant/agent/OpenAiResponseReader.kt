package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseOutputItem
import com.openai.models.responses.ResponseOutputMessage
import com.openai.models.responses.ResponseStatus
import kotlin.jvm.optionals.getOrNull

/** Reads a Responses API [Response]: the items to replay, the texts and the function calls, or the [AgentFailure]. */
internal object OpenAiResponseReader {
    private const val CONTENT_FILTER = "content_filter"
    private const val REFUSAL = "refusal"

    /**
     * The output items to append to the history verbatim, in order: reasoning items (with their encrypted content)
     * stay paired with the message or call that follows them. Kinds the agent never requests are dropped.
     * Throws the [AgentFailure] of a failed, incomplete, refused or empty response.
     */
    fun inputItems(response: Response): List<ResponseInputItem> {
        val error = statusError(response) ?: emptyReplyError(response)
        if (error != null) throw AgentFailure(error)
        return response.output().mapNotNull(::toInputItem)
    }

    fun texts(response: Response): List<String> =
        contents(response)
            .filter { it.isOutputText() }
            .map { it.asOutputText().text() }
            .filter { it.isNotBlank() }

    fun functionCalls(response: Response): List<ResponseFunctionToolCall> =
        response.output().filter { it.isFunctionCall() }.map { it.asFunctionCall() }

    private fun statusError(response: Response): AssistantError? =
        when (response.status().getOrNull()) {
            null, ResponseStatus.COMPLETED -> null
            ResponseStatus.FAILED -> OpenAiErrorMapper.mapResponseError(errorCode(response))
            ResponseStatus.INCOMPLETE -> incompleteError(response)
            else -> AssistantError.ModelRequestFailed("Unexpected response status.")
        }

    private fun errorCode(response: Response): String? =
        response
            .error()
            .getOrNull()
            ?.code()
            ?.asString()

    private fun incompleteError(response: Response): AssistantError {
        val reason =
            response
                .incompleteDetails()
                .getOrNull()
                ?.reason()
                ?.getOrNull()
                ?.asString()
        return if (reason == CONTENT_FILTER) {
            AssistantError.ModelBlocked(CONTENT_FILTER)
        } else {
            AssistantError.ModelRequestFailed("Incomplete response (${reason ?: "no reason"}).")
        }
    }

    /** A completed response with neither a text nor a call: a refusal, or nothing usable. */
    private fun emptyReplyError(response: Response): AssistantError? =
        when {
            functionCalls(response).isNotEmpty() || texts(response).isNotEmpty() -> null
            contents(response).any { it.isRefusal() } -> AssistantError.ModelBlocked(REFUSAL)
            else -> AssistantError.ModelRequestFailed("Empty response.")
        }

    private fun contents(response: Response): List<ResponseOutputMessage.Content> =
        response
            .output()
            .filter { it.isMessage() }
            .flatMap { it.asMessage().content() }

    private fun toInputItem(item: ResponseOutputItem): ResponseInputItem? =
        when {
            item.isReasoning() -> ResponseInputItem.ofReasoning(item.asReasoning())
            item.isMessage() -> ResponseInputItem.ofResponseOutputMessage(item.asMessage())
            item.isFunctionCall() -> ResponseInputItem.ofFunctionCall(item.asFunctionCall())
            else -> null
        }
}
