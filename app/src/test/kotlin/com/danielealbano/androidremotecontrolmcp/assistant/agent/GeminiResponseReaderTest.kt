package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.google.genai.types.BlockedReason
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GeminiResponseReaderTest {
    private fun failure(response: GenerateContentResponse) =
        assertThrows<AgentFailure> { GeminiResponseReader.modelContent(response) }.error

    @Test
    fun `returns candidate content verbatim`() {
        val content =
            Content
                .builder()
                .role("model")
                .parts(listOf(Part.fromText("hi")))
                .build()

        assertSame(content, GeminiResponseReader.modelContent(GeminiTestResponses.response(content)))
    }

    @Test
    fun `prompt block reason fails with blocked`() {
        val error = failure(GeminiTestResponses.promptBlocked(BlockedReason.Known.SAFETY))

        assertEquals(AssistantError.GeminiBlocked("SAFETY"), error)
    }

    @Test
    fun `empty content with SAFETY finish reason fails with blocked`() {
        assertEquals(
            AssistantError.GeminiBlocked("SAFETY"),
            failure(GeminiTestResponses.response(null, FinishReason.Known.SAFETY)),
        )
    }

    @Test
    fun `empty content with STOP and no candidates fail with request failed`() {
        assertEquals(
            true,
            failure(GeminiTestResponses.response(null)) is AssistantError.GeminiRequestFailed,
        )
        assertEquals(
            true,
            failure(GenerateContentResponse.builder().build()) is AssistantError.GeminiRequestFailed,
        )
    }

    @Test
    fun `texts skips thought and blank parts and functionCalls keeps order`() {
        val content =
            Content
                .builder()
                .role("model")
                .parts(
                    listOf(
                        Part
                            .builder()
                            .text("thinking")
                            .thought(true)
                            .build(),
                        Part.fromText("  "),
                        Part.fromText("answer"),
                    ),
                ).build()

        assertEquals(listOf("answer"), GeminiResponseReader.texts(content))
        val calls =
            GeminiTestResponses
                .calls("a" to emptyMap(), "b" to emptyMap())
                .candidates()
                .get()[0]
                .content()
                .get()
        assertEquals(listOf("a", "b"), GeminiResponseReader.functionCalls(calls).map { it.name().get() })
    }
}
