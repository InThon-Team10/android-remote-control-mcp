package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.DisabledVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperConversationControlTest {
    @Test
    fun `opening text input starts no session and stopping cancels text guidance`() =
        runTest {
            val session = mockk<AssistantSession>(relaxed = true)
            val control = TextHelperConversationControl(session)
            assertTrue(control.start().isSuccess)
            assertFalse(control.active.first())
            verify { session wasNot Called }
            control.stop()
            verify(exactly = 1) { session.stop() }
        }

    @Test
    fun `realtime stays unavailable even from the debug entry`() =
        runTest {
            val voice = DisabledVoiceSession()
            assertTrue(voice.start().isFailure)
            voice.sendText("hello")
            assertEquals(LiveVoiceState.Idle, voice.state.value)
        }
}
