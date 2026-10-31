package com.danielealbano.androidremotecontrolmcp.debug.assistant

import android.app.Activity
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.NoticeKind
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantTestViewModelTest {
    private val settings = mockk<AssistantSettings>(relaxed = true)
    private val session = mockk<AssistantSession>(relaxed = true)
    private val starter = mockk<AssistantServerStarter>()
    private val liveVoice = mockk<LiveVoiceSession>(relaxed = true)
    private val activity = mockk<Activity>()
    private lateinit var viewModel: AssistantTestViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.config } returns flowOf(AssistantConfig())
        every { liveVoice.state } returns MutableStateFlow(LiveVoiceState.Idle)
        viewModel = AssistantTestViewModel(settings, session, starter, liveVoice)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `send ensures the server is running then sends`() {
        coEvery { starter.ensureRunning(activity) } returns Result.success(Unit)

        viewModel.send(activity, "hi")

        coVerifyOrder {
            starter.ensureRunning(activity)
            session.send("hi")
        }
    }

    @Test
    fun `send reports the error and does not send when ensureRunning fails`() {
        coEvery { starter.ensureRunning(activity) } returns
            Result.failure(AssistantException(AssistantError.ServerStartBlocked))

        viewModel.send(activity, "hi")

        verify { session.reportError(AssistantError.ServerStartBlocked) }
        verify(exactly = 0) { session.send(any()) }
    }

    @Test
    fun `enable without privacy acceptance sets a message and does not enable`() {
        viewModel.setEnabled(true, privacyAccepted = false)

        assertEquals("Accept the privacy notice first", viewModel.message.value)
        coVerify(exactly = 0) { settings.setEnabled(any()) }
    }

    @Test
    fun `enable with privacy acceptance accepts then enables`() {
        viewModel.setEnabled(true, privacyAccepted = true)

        coVerifyOrder {
            settings.acceptPrivacyNotice()
            settings.setEnabled(true)
        }
    }

    @Test
    fun `invalid step limit sets a message and is not stored`() {
        viewModel.setStepLimit("1000")

        assertEquals(true, viewModel.message.value?.contains("between"))
        coVerify(exactly = 0) { settings.setStepLimit(any()) }
    }

    @Test
    fun `startTalk ensures the server is running then starts the live session`() {
        coEvery { starter.ensureRunning(activity) } returns Result.success(Unit)
        coEvery { liveVoice.start() } returns Result.success(Unit)

        viewModel.startTalk(activity)

        coVerifyOrder {
            starter.ensureRunning(activity)
            liveVoice.start()
        }
        verify(exactly = 0) { session.reportError(any()) }
    }

    @Test
    fun `startTalk reports a live start failure`() {
        coEvery { starter.ensureRunning(activity) } returns Result.success(Unit)
        coEvery { liveVoice.start() } returns Result.failure(AssistantException(AssistantError.VoiceDisabled))

        viewModel.startTalk(activity)

        verify { session.reportError(AssistantError.VoiceDisabled) }
    }

    @Test
    fun `startTalk does not start the live session when the server cannot start`() {
        coEvery { starter.ensureRunning(activity) } returns
            Result.failure(AssistantException(AssistantError.BearerDisabled))

        viewModel.startTalk(activity)

        coVerify(exactly = 0) { liveVoice.start() }
        verify { session.reportError(AssistantError.BearerDisabled) }
    }

    @Test
    fun `endTalk stops the live session`() {
        viewModel.endTalk()

        coVerify { liveVoice.stop() }
    }

    @Test
    fun `rowText renders each item kind`() {
        assertEquals("You: a", ChatItem.User(1, "a").rowText())
        assertEquals("Notice: stopped", ChatItem.Notice(3, NoticeKind.Stopped).rowText())
        assertEquals("Confirm t: PENDING", ChatItem.Confirmation(4, "c", "t", null).rowText())
    }
}
