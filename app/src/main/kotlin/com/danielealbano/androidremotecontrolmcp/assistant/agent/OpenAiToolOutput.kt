package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.openai.models.responses.ResponseFunctionCallOutputItem
import com.openai.models.responses.ResponseInputImageContent
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.ResponseInputTextContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Base64

/** Builds the `function_call_output` item answering one Responses API function call. */
internal object OpenAiToolOutput {
    const val ERROR_PREFIX = "ERROR: "
    const val DENIED_MESSAGE = "The user denied this tool call."
    const val UNKNOWN_TOOL_MESSAGE = "Unknown tool."
    const val INVALID_ARGUMENTS_MESSAGE = "The arguments are not a JSON object."
    private const val IMAGE_ATTACHED = "Image attached."

    /**
     * Text blocks (already carrying the server's untrusted-content warning) are joined, an error result is prefixed
     * with [ERROR_PREFIX]; images follow the text as data-URL image items.
     */
    fun fromResult(
        callId: String,
        result: CallToolResult,
    ): ResponseInputItem {
        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        val images = result.content.filterIsInstance<ImageContent>().mapNotNull(::toImageItem)
        val body = text.ifEmpty { if (images.isEmpty()) "" else IMAGE_ATTACHED }
        return build(callId, if (result.isError == true) ERROR_PREFIX + body else body, images)
    }

    fun denied(callId: String): ResponseInputItem = errorItem(callId, DENIED_MESSAGE)

    fun unknownTool(callId: String): ResponseInputItem = errorItem(callId, UNKNOWN_TOOL_MESSAGE)

    private fun errorItem(
        callId: String,
        message: String,
    ): ResponseInputItem = build(callId, ERROR_PREFIX + message, emptyList())

    fun errorOutput(
        callId: String,
        message: String,
    ): ResponseInputItem = errorItem(callId, message)

    fun invalidArguments(callId: String): ResponseInputItem = errorItem(callId, INVALID_ARGUMENTS_MESSAGE)

    /** Undecodable image data is dropped rather than sent. */
    private fun toImageItem(image: ImageContent): ResponseFunctionCallOutputItem? =
        runCatching { Base64.getDecoder().decode(image.data) }
            .getOrNull()
            ?.let {
                ResponseFunctionCallOutputItem.ofInputImage(
                    ResponseInputImageContent
                        .builder()
                        .imageUrl("data:${image.mimeType};base64,${image.data}")
                        .build(),
                )
            }

    private fun build(
        callId: String,
        output: String,
        images: List<ResponseFunctionCallOutputItem>,
    ): ResponseInputItem {
        val builder = ResponseInputItem.FunctionCallOutput.builder().callId(callId)
        if (images.isEmpty()) {
            builder.output(output)
        } else {
            val text = ResponseInputTextContent.builder().text(output).build()
            val items = listOf(ResponseFunctionCallOutputItem.ofInputText(text)) + images
            builder.outputOfResponseFunctionCallOutputItemList(items)
        }
        return ResponseInputItem.ofFunctionCallOutput(builder.build())
    }
}
