package com.danielealbano.androidremotecontrolmcp.debug.assistant

import android.app.Activity
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.NoticeKind
import com.danielealbano.androidremotecontrolmcp.assistant.voice.VoiceClip
import com.danielealbano.androidremotecontrolmcp.assistant.voice.VoiceRecorder
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
    private val recorder = mockk<VoiceRecorder>(relaxed = true)
    private val maxDuration = MutableStateFlow(false)
    private val activity = mockk<Activity>()
    private lateinit var viewModel: AssistantTestViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.config } returns flowOf(AssistantConfig())
        every { recorder.isRecording } returns MutableStateFlow(false)
        every { recorder.maxDurationReached } returns maxDuration
        viewModel = AssistantTestViewModel(settings, session, starter, recorder)
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
    fun `stopVoice sends the clip`() {
        coEvery { recorder.stop() } returns Result.success(VoiceClip(byteArrayOf(1), 2_000L))

        viewModel.stopVoice()

        verify { session.sendVoice(any(), 2_000L) }
    }

    @Test
    fun `voice start and stop failures are reported`() {
        coEvery { recorder.start() } returns Result.failure(IllegalStateException())
        coEvery { recorder.stop() } returns Result.failure(IllegalStateException())

        viewModel.startVoice()
        viewModel.stopVoice()

        verify(exactly = 2) { session.reportError(AssistantError.VoiceRecordingFailed) }
    }

    @Test
    fun `reaching the max duration stops the recording`() {
        coEvery { recorder.stop() } returns Result.success(VoiceClip(byteArrayOf(1), 60_000L))

        maxDuration.value = true

        coVerify { recorder.stop() }
        verify { session.sendVoice(any(), 60_000L) }
    }

    @Test
    fun `onCleared cancels the recorder`() {
        viewModel.javaClass
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(viewModel)

        coVerify { recorder.cancel() }
    }

    @Test
    fun `rowText renders each item kind`() {
        assertEquals("You: a", ChatItem.User(1, "a").rowText())
        assertEquals("Voice (12 s)", ChatItem.UserVoice(2, 12_000L).rowText())
        assertEquals("Notice: stopped", ChatItem.Notice(3, NoticeKind.Stopped).rowText())
        assertEquals("Confirm t: PENDING", ChatItem.Confirmation(4, "c", "t", null).rowText())
    }
}
