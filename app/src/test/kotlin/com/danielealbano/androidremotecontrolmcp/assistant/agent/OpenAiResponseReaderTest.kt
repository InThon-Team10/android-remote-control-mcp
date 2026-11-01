package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.call
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.message
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.reasoning
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.refusal
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiTestResponses.response
import com.openai.models.responses.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OpenAiResponseReaderTest {
    private fun failure(response: Response): AssistantError {
        val failure = assertThrows<AgentFailure> { OpenAiResponseReader.inputItems(response) }
        return failure.error
    }

    @Test
    fun `output items are replayed in order with the encrypted reasoning`() {
        val items = OpenAiResponseReader.inputItems(response(reasoning(), message("Hi"), call("android_tap")))

        assertEquals(3, items.size)
        assertEquals(OpenAiTestResponses.ENCRYPTED_REASONING, items[0].asReasoning().encryptedContent().get())
        assertTrue(items[1].isResponseOutputMessage())
        assertEquals("android_tap", items[2].asFunctionCall().name())
    }

    @Test
    fun `texts skip blank parts and calls are read in order`() {
        val reply = response(message(" "), message("a"), call("one", callId = "c1"), call("two", callId = "c2"))

        assertEquals(listOf("a"), OpenAiResponseReader.texts(reply))
        assertEquals(listOf("c1", "c2"), OpenAiResponseReader.functionCalls(reply).map { it.callId() })
    }

    @Test
    fun `a response without status is read like a completed one`() {
        val reply = response(reasoning(), message("Hi"), status = null)

        assertEquals(2, OpenAiResponseReader.inputItems(reply).size)
    }

    @Test
    fun `failed response maps its error code`() {
        val reply = response(status = "failed", extra = ""","error":{"code":"server_error","message":"m"}""")

        assertEquals(AssistantError.ModelUnavailable, failure(reply))
    }

    @Test
    fun `incomplete response is blocked on content filter and failed otherwise`() {
        val filtered =
            response(
                message("x"),
                status = "incomplete",
                extra = ""","incomplete_details":{"reason":"content_filter"}""",
            )
        val truncated =
            response(
                message("x"),
                status = "incomplete",
                extra = ""","incomplete_details":{"reason":"max_output_tokens"}""",
            )

        assertEquals(AssistantError.ModelBlocked("content_filter"), failure(filtered))
        assertEquals(AssistantError.ModelRequestFailed("Incomplete response (max_output_tokens)."), failure(truncated))
    }

    @Test
    fun `unexpected status fails`() {
        assertEquals(
            AssistantError.ModelRequestFailed("Unexpected response status."),
            failure(response(message("x"), status = "in_progress")),
        )
    }

    @Test
    fun `refusal without text or call is blocked`() {
        assertEquals(AssistantError.ModelBlocked("refusal"), failure(response(reasoning(), refusal("no"))))
    }

    @Test
    fun `response with neither text nor call fails as empty`() {
        assertEquals(AssistantError.ModelRequestFailed("Empty response."), failure(response(reasoning())))
        assertEquals(AssistantError.ModelRequestFailed("Empty response."), failure(response()))
    }
}
