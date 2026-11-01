package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("speechAllowed")
class HelperSpeechPolicyTest {
    @Test
    fun `reads a ready enabled caption`() {
        assertTrue(speechAllowed(enabled = true, ready = true, text = "말풍선 내용"))
    }

    @Test
    fun `stays silent when speech is switched off`() {
        assertFalse(speechAllowed(enabled = false, ready = true, text = "안녕하세요"))
    }

    @Test
    fun `stays silent while the engine is not ready`() {
        assertFalse(speechAllowed(enabled = true, ready = false, text = "안녕하세요"))
    }

    @Test
    fun `stays silent for blank text`() {
        assertFalse(speechAllowed(enabled = true, ready = true, text = "  "))
    }
}
