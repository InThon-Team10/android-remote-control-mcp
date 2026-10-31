package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import com.google.genai.types.FunctionCall
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.random.Random

@DisplayName("ToolResultMapper fuzz")
class ToolResultMapperFuzzTest {
    private val mimes = listOf("image/png", "image/jpeg", "image/webp", "", "text/plain", "application/octet-stream")

    private fun call(random: Random): FunctionCall {
        val builder = FunctionCall.builder().name(AssistantFuzzGen.identifier(random))
        if (random.nextBoolean()) builder.id("id-" + random.nextInt())
        return builder.build()
    }

    private fun validImage(random: Random): Pair<ImageContent, ByteArray> {
        val bytes = random.nextBytes(random.nextInt(64))
        return ImageContent(Base64.getEncoder().encodeToString(bytes), mimes[random.nextInt(mimes.size)]) to bytes
    }

    @Test
    fun `random results map to the documented text and image parts`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(200) { index ->
                val label = "seed=$seed index=$index"
                val texts = mutableListOf<String>()
                val images = mutableListOf<ByteArray>()
                val content =
                    List(random.nextInt(6)) {
                        if (random.nextBoolean()) {
                            AssistantFuzzGen.string(random, 200).also { texts += it }.let(::TextContent)
                        } else {
                            val (image, bytes) = validImage(random)
                            images += bytes
                            image
                        }
                    }
                val isError = listOf(null, true, false)[random.nextInt(3)]
                val call = call(random)

                val result = CallToolResult(content = content, isError = isError)
                val response = ToolResultMapper.fromResult(call, result).functionResponse().get()

                val joined = texts.joinToString("\n")
                val body = joined.ifEmpty { if (images.isEmpty()) "" else "Image attached." }
                val key = if (isError == true) "error" else "result"
                assertEquals(mapOf(key to body), response.response().get(), label)
                assertEquals(call.name().get(), response.name().get(), label)
                assertEquals(call.id().orElse(null), response.id().orElse(null), label)
                val attached = response.parts().orElse(emptyList())
                assertEquals(images.size, attached.size, label)
                images.forEachIndexed { i, bytes ->
                    assertArrayEquals(
                        bytes,
                        attached[i]
                            .inlineData()
                            .get()
                            .data()
                            .get(),
                        label,
                    )
                }
            }
        }
    }
}
