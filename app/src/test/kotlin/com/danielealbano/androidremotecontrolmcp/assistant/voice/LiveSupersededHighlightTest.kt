package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.mcp.tools.ElderToolGuidance
import com.google.genai.types.FunctionResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("isSupersededHighlight")
class LiveSupersededHighlightTest {
    private fun response(body: Map<String, Any>): FunctionResponse =
        FunctionResponse
            .builder()
            .name("android_tap_node")
            .response(body)
            .build()

    @Test
    fun `a highlight replaced by a newer one is recognised`() {
        assertTrue(isSupersededHighlight(response(mapOf("error" to ElderToolGuidance.SUPERSEDED_TEXT))))
    }

    @Test
    fun `other errors and results are answered`() {
        assertFalse(isSupersededHighlight(response(mapOf("error" to ElderToolGuidance.CANCELLED_TEXT))))
        assertFalse(isSupersededHighlight(response(mapOf("result" to ElderToolGuidance.USER_ACTED_TEXT))))
    }
}
