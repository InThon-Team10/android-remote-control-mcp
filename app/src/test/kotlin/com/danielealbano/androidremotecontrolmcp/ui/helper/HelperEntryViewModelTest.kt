package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.HelperFlag
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("HelperEntryViewModel")
class HelperEntryViewModelTest {
    private val config = MutableStateFlow(AssistantConfig())
    private val serverStatus: StateFlow<ServerStatus> = MutableStateFlow(ServerStatus.Stopped)
    private val conversationActive = MutableStateFlow(false)
    private val settings = mockk<AssistantSettings>(relaxed = true)
    private val bubble = mockk<HelperBubble>(relaxed = true)
    private val serverSwitch = mockk<HelperServerSwitch>(relaxed = true)
    private val accessibility = mockk<AccessibilityServiceProvider>()
    private val conversation = mockk<HelperConversationControl>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val readyConfig =
        AssistantConfig(privacyNoticeAccepted = true, apiKeyConfigured = true, helperIntroSeen = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(McpServerService.Companion)
        every { McpServerService.serverStatus } returns serverStatus
        every { settings.config } returns config
        coEvery { settings.getConfig() } answers { config.value }
        every { accessibility.isReady() } returns true
        every { conversation.active } returns conversationActive
        coEvery { conversation.start() } returns Result.success(Unit)
        coEvery { serverSwitch.ensureRunning(any()) } returns Result.success(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(McpServerService.Companion)
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        HelperEntryViewModel(handle, settings, bubble, serverSwitch, accessibility, conversation)

    @Test
    fun `first launch shows welcome and later launches show home`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(HelperEntryPage.WELCOME, expectMostRecentItem().model.page)
            }
            config.value = AssistantConfig(helperIntroSeen = true)
            viewModel().uiState.test {
                assertEquals(HelperEntryPage.HOME, expectMostRecentItem().model.page)
            }
        }

    @Test
    fun `reaching home stores the intro flag once`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.HOME) }
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { settings.setHelperFlag(HelperFlag.INTRO_SEEN, true) }
            config.value = AssistantConfig(helperIntroSeen = true)
            vm.uiState.test {
                vm.navigate(HelperEntryModel::settings)
                vm.navigate(HelperEntryModel::home)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { settings.setHelperFlag(HelperFlag.INTRO_SEEN, true) }
        }

    @Test
    fun `show intro again ends the conversation resets the flag and shows welcome`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate(HelperEntryModel::settings)
                vm.showIntroAgain()
                assertEquals(HelperEntryPage.WELCOME, expectMostRecentItem().model.page)
            }
            coVerify { conversation.stop() }
            coVerify { settings.setHelperFlag(HelperFlag.INTRO_SEEN, false) }
        }

    @Test
    fun `entering practice ends a live conversation before the page changes`() =
        runTest {
            val vm = viewModel()
            var pageWhenStopped: HelperEntryPage? = null
            coEvery { conversation.stop() } answers { pageWhenStopped = vm.uiState.value.model.page }
            config.value = AssistantConfig(helperIntroSeen = true)
            vm.uiState.test {
                vm.startPractice()
                assertEquals(HelperEntryPage.PRACTICE, expectMostRecentItem().model.page)
            }
            assertEquals(HelperEntryPage.HOME, pageWhenStopped)
            coVerify { conversation.stop() }
        }

    @Test
    fun `navigation and practice step survive recreation through the saved state`() =
        runTest {
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            first.uiState.test {
                first.navigate(HelperEntryModel::practice)
                first.navigate(HelperEntryModel::advance)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel(handle).uiState.test {
                val model = expectMostRecentItem().model
                assertEquals(HelperEntryPage.PRACTICE, model.page)
                assertEquals(HelperPracticeStep.ASK, model.step)
            }
        }

    @Test
    fun `resume reports accessibility and permissions`() =
        runTest {
            every { accessibility.isReady() } returns false
            val vm = viewModel()
            vm.uiState.test {
                vm.onResume(context, PermissionSnapshot(notifications = true, microphone = true))
                val state = expectMostRecentItem()
                assertFalse(state.accessibilityReady)
                assertTrue(state.permissions.notifications && state.permissions.microphone)
            }
        }

    @Test
    fun `consent is stored before setup continues and the helper is not enabled yet`() =
        runTest {
            every { accessibility.isReady() } returns false
            val vm = viewModel()
            vm.uiState.test {
                vm.setup.acceptConsent(context)
                assertEquals(HelperEntryPage.PERMISSION, expectMostRecentItem().model.page)
            }
            coVerify { settings.acceptPrivacyNotice() }
            coVerify(exactly = 0) { settings.setEnabled(any()) }
        }

    @Test
    fun `consent storage failure shows the consent error`() =
        runTest {
            coEvery { settings.acceptPrivacyNotice() } throws IOException("disk")
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.CONSENT) }
                vm.setup.acceptConsent(context)
                val state = expectMostRecentItem()
                assertEquals(R.string.helper_consent_error, state.errorRes)
                assertEquals(HelperEntryPage.CONSENT, state.model.page)
                assertFalse(state.busy)
            }
        }

    @Test
    fun `continue setup skips finished steps and turns on`() =
        runTest {
            config.value = readyConfig
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.INVITE) }
                vm.setup.continueSetup(context)
                assertEquals(HelperEntryPage.HOME, expectMostRecentItem().model.page)
            }
            coVerify { serverSwitch.ensureRunning(context) }
            coVerify { settings.setEnabled(true) }
        }

    @Test
    fun `microphone is asked only before the intro was seen`() =
        runTest {
            config.value = readyConfig.copy(helperIntroSeen = false)
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.INVITE) }
                vm.setup.continueSetup(context)
                assertEquals(HelperEntryPage.MICROPHONE, expectMostRecentItem().model.page)
            }
            coVerify(exactly = 0) { settings.setEnabled(any()) }
        }

    @Test
    fun `granted microphone before the intro enables voice and turns on`() =
        runTest {
            config.value = readyConfig.copy(helperIntroSeen = false)
            val vm = viewModel()
            vm.uiState.test {
                vm.onResume(context, PermissionSnapshot(microphone = true))
                vm.navigate { it.page(HelperEntryPage.INVITE) }
                vm.setup.continueSetup(context)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { settings.setVoiceEnabled(true) }
            coVerify { settings.setEnabled(true) }
        }

    @Test
    fun `microphone answer records the voice choice and turns on`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.MICROPHONE) }
                vm.setup.onMicrophoneAnswered(context, granted = true)
                assertEquals(HelperEntryPage.HOME, expectMostRecentItem().model.page)
            }
            coVerify { settings.setVoiceEnabled(true) }
            coVerify { settings.setEnabled(true) }
        }

    @Test
    fun `denied microphone answer records no voice and skip only turns on`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.MICROPHONE) }
                vm.setup.onMicrophoneAnswered(context, granted = false)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { settings.setVoiceEnabled(false) }
            vm.setup.turnOn(context)
            coVerify(exactly = 1) { settings.setVoiceEnabled(any()) }
            coVerify(exactly = 2) { settings.setEnabled(true) }
        }

    @Test
    fun `microphone answers outside the microphone page change nothing`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.setup.onMicrophoneAnswered(context, granted = true)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { settings.setVoiceEnabled(any()) }
        }

    @Test
    fun `returning from accessibility settings continues setup`() =
        runTest {
            config.value = readyConfig
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.PERMISSION) }
                vm.onResume(context, PermissionSnapshot())
                assertEquals(HelperEntryPage.HOME, expectMostRecentItem().model.page)
            }
            coVerify { settings.setEnabled(true) }
        }

    @Test
    fun `missing AI key stops at home without starting the server`() =
        runTest {
            config.value = readyConfig.copy(apiKeyConfigured = false)
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate { it.page(HelperEntryPage.INVITE) }
                vm.setup.continueSetup(context)
                assertEquals(HelperEntryPage.HOME, expectMostRecentItem().model.page)
            }
            coVerify(exactly = 0) { serverSwitch.ensureRunning(any()) }
        }

    @Test
    fun `reported errors show and dismiss`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.reportError(R.string.helper_onboard_permission_error)
                assertEquals(R.string.helper_onboard_permission_error, expectMostRecentItem().errorRes)
                vm.dismissError()
                assertEquals(null, expectMostRecentItem().errorRes)
            }
        }

    @Test
    fun `turn on failure shows the error and does not enable the helper`() =
        runTest {
            coEvery { serverSwitch.ensureRunning(any()) } returns
                Result.failure(AssistantException(AssistantError.ServerStartBlocked))
            val vm = viewModel()
            vm.uiState.test {
                vm.setup.turnOn(context)
                val state = expectMostRecentItem()
                assertEquals(R.string.entry_error_blocked, state.errorRes)
                assertFalse(state.busy)
            }
            coVerify(exactly = 0) { settings.setEnabled(true) }
        }

    @Test
    fun `other start failures show the generic error`() =
        runTest {
            coEvery { serverSwitch.ensureRunning(any()) } returns
                Result.failure(AssistantException(AssistantError.ServerStartFailed("x")))
            val vm = viewModel()
            vm.uiState.test {
                vm.setup.turnOn(context)
                assertEquals(R.string.entry_error_start, expectMostRecentItem().errorRes)
            }
        }

    @Test
    fun `resume on home while enabled ensures the server again`() =
        runTest {
            config.value = AssistantConfig(helperIntroSeen = true, enabled = true)
            val vm = viewModel()
            vm.uiState.test {
                vm.onResume(context, PermissionSnapshot())
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { serverSwitch.ensureRunning(context) }
        }

    @Test
    fun `resume while disabled does not start the server`() =
        runTest {
            config.value = AssistantConfig(helperIntroSeen = true)
            viewModel().onResume(context, PermissionSnapshot())
            coVerify(exactly = 0) { serverSwitch.ensureRunning(any()) }
        }

    @Test
    fun `turn off ends the conversation disables the helper and stops the server`() =
        runTest {
            viewModel().turnOff(context)
            coVerifyOrder {
                conversation.stop()
                settings.setEnabled(false)
                serverSwitch.stop(context)
            }
        }

    @Test
    fun `ask starts the conversation and runs the callback`() =
        runTest {
            var started = false
            viewModel().openHelper { started = true }
            coVerify { conversation.start() }
            assertTrue(started)
        }

    @Test
    fun `ask failure shows its error and does not open`() =
        runTest {
            coEvery { conversation.start() } returns
                Result.failure(AssistantException(AssistantError.MicrophonePermissionMissing))
            val vm = viewModel()
            var started = false
            vm.uiState.test {
                vm.openHelper { started = true }
                assertEquals(R.string.bubble_error_mic_permission, expectMostRecentItem().errorRes)
            }
            assertFalse(started)
        }

    @Test
    fun `the bubble is suppressed off home and released when collection ends`() =
        runTest {
            val vm = viewModel()
            config.value = AssistantConfig(helperIntroSeen = true)
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { vm.suppressBubbleOffHome() }
            vm.navigate(HelperEntryModel::settings)
            verify { bubble.setSuppressed(true) }
            job.cancel()
            job.join()
            verify { bubble.setSuppressed(false) }
        }

    @Test
    fun `settings and bubble actions are delegated`() =
        runTest {
            val vm = viewModel()
            vm.editor.setVoice(true)
            vm.editor.setSpeech(false)
            vm.openInput()
            coVerify { settings.setVoiceEnabled(true) }
            coVerify { settings.setHelperFlag(HelperFlag.SPEECH_ENABLED, false) }
            verify { bubble.openInput() }
        }
}
