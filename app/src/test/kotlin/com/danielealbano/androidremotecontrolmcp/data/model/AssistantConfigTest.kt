package com.danielealbano.androidremotecontrolmcp.data.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AssistantConfig")
class AssistantConfigTest {
    @Test
    fun `defaults match the owner decisions`() {
        val config = AssistantConfig()

        assertFalse(config.enabled)
        assertFalse(config.voiceEnabled)
        assertEquals("gemini-flash-latest", config.model)
        assertEquals(30, config.stepLimit)
        assertFalse(config.privacyNoticeAccepted)
        assertFalse(config.apiKeyConfigured)
    }

    @Test
    fun `the text model is pinned to the latest Flash and voice keeps the 3_8 live model`() {
        assertEquals(listOf("gemini-flash-latest"), AssistantConfig.MODELS)
        assertEquals("gemini-3.8-live", AssistantConfig.LIVE_MODEL)
    }

    @Test
    fun `validateStepLimit accepts the bounds 1 and 100`() {
        assertTrue(AssistantConfig.validateStepLimit(1).isSuccess)
        assertTrue(AssistantConfig.validateStepLimit(100).isSuccess)
    }

    @Test
    fun `validateStepLimit rejects 0, 101 and negatives`() {
        listOf(0, 101, -1, Int.MIN_VALUE).forEach { value ->
            val result = AssistantConfig.validateStepLimit(value)
            assertTrue(result.isFailure, "expected failure for $value")
            assertInstanceOf(IllegalArgumentException::class.java, result.exceptionOrNull())
        }
    }

    @Test
    fun `confirmation tools are exactly the six D10 tools`() {
        assertEquals(
            setOf(
                "close_app",
                "share_file_via_web",
                "take_camera_photo",
                "save_camera_photo",
                "save_camera_video",
                "set_clipboard",
            ),
            AssistantConfig.CONFIRMATION_TOOLS,
        )
    }
}
