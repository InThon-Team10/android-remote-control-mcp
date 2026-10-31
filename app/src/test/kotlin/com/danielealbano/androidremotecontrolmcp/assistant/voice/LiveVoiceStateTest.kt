package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LiveVoiceState.isActive")
class LiveVoiceStateTest {
    @Test
    fun `connecting, listening and speaking are active`() {
        assertTrue(LiveVoiceState.Connecting.isActive())
        assertTrue(LiveVoiceState.Listening.isActive())
        assertTrue(LiveVoiceState.Speaking.isActive())
    }

    @Test
    fun `idle and error are not active`() {
        assertFalse(LiveVoiceState.Idle.isActive())
        assertFalse(LiveVoiceState.Error(AssistantError.VoiceRecordingFailed).isActive())
    }
}
