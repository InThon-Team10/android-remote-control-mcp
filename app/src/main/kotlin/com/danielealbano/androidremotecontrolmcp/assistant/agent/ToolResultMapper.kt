package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.FunctionResponsePart
import com.google.genai.types.Part
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.util.Base64
import kotlin.jvm.optionals.getOrNull

/** Builds the `functionResponse` [Part] answering one Gemini function call. */
internal object ToolResultMapper {
    const val DENIED_MESSAGE = "The user denied this tool call."
    const val UNKNOWN_TOOL_MESSAGE = "Unknown tool."
    private const val IMAGE_ATTACHED = "Image attached."

    /** Text blocks (already carrying the server's untrusted-content warning) are joined; images become parts. */
    fun fromResult(
        call: FunctionCall,
        result: CallToolResult,
    ): Part {
        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        val images = result.content.filterIsInstance<ImageContent>().mapNotNull(::toImagePart)
        val body = text.ifEmpty { if (images.isEmpty()) "" else IMAGE_ATTACHED }
        val key = if (result.isError == true) "error" else "result"
        return build(call, mapOf(key to body), images)
    }

    fun denied(call: FunctionCall): Part = build(call, mapOf("error" to DENIED_MESSAGE), emptyList())

    fun unknownTool(call: FunctionCall): Part = build(call, mapOf("error" to UNKNOWN_TOOL_MESSAGE), emptyList())

    private fun toImagePart(image: ImageContent): FunctionResponsePart? =
        runCatching { Base64.getDecoder().decode(image.data) }
            .getOrNull()
            ?.let { FunctionResponsePart.fromBytes(it, image.mimeType) }

    private fun build(
        call: FunctionCall,
        response: Map<String, Any>,
        images: List<FunctionResponsePart>,
    ): Part {
        val builder = FunctionResponse.builder().name(call.name().getOrNull().orEmpty()).response(response)
        call.id().getOrNull()?.let { builder.id(it) }
        if (images.isNotEmpty()) builder.parts(images)
        return Part.builder().functionResponse(builder.build()).build()
    }
}
