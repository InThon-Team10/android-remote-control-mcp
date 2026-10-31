package com.danielealbano.androidremotecontrolmcp.ui.helper

import app.cash.turbine.test
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("LiveHelperConversationControl")
class HelperConversationControlTest {
    private val state = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
    private val session = mockk<LiveVoiceSession>(relaxed = true)

    init {
        every { session.state } returns state
    }

    @Test
    fun `active follows the live session states`() =
        runTest {
            LiveHelperConversationControl(session).active.test {
                assertEquals(false, awaitItem())
                state.value = LiveVoiceState.Connecting
                assertEquals(true, awaitItem())
                state.value = LiveVoiceState.Speaking
                state.value = LiveVoiceState.Error(AssistantError.ServerStartBlocked)
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `start and stop delegate to the session`() =
        runTest {
            coEvery { session.start() } returns Result.success(Unit)
            val control = LiveHelperConversationControl(session)

            assertTrue(control.start().isSuccess)
            control.stop()
            control.stop()

            coVerify(exactly = 1) { session.start() }
            coVerify(exactly = 2) { session.stop() }
        }
}
