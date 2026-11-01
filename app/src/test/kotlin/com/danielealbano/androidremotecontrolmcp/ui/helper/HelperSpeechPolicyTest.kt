package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("speechAllowed")
class HelperSpeechPolicyTest {
    @Test
    fun `speaks when enabled, ready, no live conversation and text present`() {
        assertTrue(speechAllowed(enabled = true, ready = true, liveConversation = false, text = "안녕하세요"))
    }

    @Test
    fun `stays silent during a live conversation`() {
        assertFalse(speechAllowed(enabled = true, ready = true, liveConversation = true, text = "안녕하세요"))
    }

    @Test
    fun `stays silent when speech is switched off`() {
        assertFalse(speechAllowed(enabled = false, ready = true, liveConversation = false, text = "안녕하세요"))
    }

    @Test
    fun `stays silent while the engine is not ready`() {
        assertFalse(speechAllowed(enabled = true, ready = false, liveConversation = false, text = "안녕하세요"))
    }

    @Test
    fun `stays silent for blank text`() {
        assertFalse(speechAllowed(enabled = true, ready = true, liveConversation = false, text = "  "))
    }
}
