package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.HelperFlag
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("HelperEntryViewModel")
class HelperEntryViewModelTest {
    private val config = MutableStateFlow(AssistantConfig())
    private val bubbleStatus = MutableStateFlow(HelperBubbleStatus.OFF)
    private val serverStatus: StateFlow<ServerStatus> = MutableStateFlow(ServerStatus.Stopped)
    private val settings = mockk<AssistantSettings>(relaxed = true)
    private val bubble = mockk<HelperBubble>(relaxed = true)
    private val starter = mockk<AssistantServerStarter>()
    private val accessibility = mockk<AccessibilityServiceProvider>()
    private val context = mockk<Context>(relaxed = true)
    private val readyConfig = AssistantConfig(privacyNoticeAccepted = true, apiKeyConfigured = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(McpServerService.Companion)
        every { McpServerService.serverStatus } returns serverStatus
        every { settings.config } returns config
        coEvery { settings.getConfig() } answers { config.value }
        every { bubble.status } returns bubbleStatus
        every { accessibility.isReady() } returns true
        coEvery { starter.ensureRunning(any()) } returns Result.success(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(McpServerService.Companion)
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        HelperEntryViewModel(handle, settings, bubble, starter, accessibility)

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
            config.value = AssistantConfig()
        }

    @Test
    fun `start setup navigates and stores the intro flag`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.startSetup()
                assertEquals(HelperEntryPage.SETUP, expectMostRecentItem().model.page)
            }
            coVerify { settings.setHelperFlag(HelperFlag.INTRO_SEEN, true) }
        }

    @Test
    fun `show intro again resets the flag and shows welcome`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate(HelperEntryModel::settings)
                vm.showIntroAgain()
                assertEquals(HelperEntryPage.WELCOME, expectMostRecentItem().model.page)
            }
            coVerify { settings.setHelperFlag(HelperFlag.INTRO_SEEN, false) }
        }

    @Test
    fun `navigation survives recreation through the saved state`() =
        runTest {
            val handle = SavedStateHandle()
            viewModel(handle).navigate(HelperEntryModel::settings)
            viewModel(handle).uiState.test {
                assertEquals(HelperEntryPage.SETTINGS, expectMostRecentItem().model.page)
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
    fun `turn on starts the server enables the helper and goes home`() =
        runTest {
            config.value = readyConfig
            val vm = viewModel()
            vm.uiState.test {
                vm.navigate(HelperEntryModel::setup)
                vm.turnOn(context)
                assertEquals(HelperEntryPage.HOME, expectMostRecentItem().model.page)
            }
            coVerify { starter.ensureRunning(context) }
            coVerify { settings.setEnabled(true) }
            config.value = AssistantConfig()
        }

    @Test
    fun `turn on failure shows the error and does not enable the helper`() =
        runTest {
            coEvery { starter.ensureRunning(any()) } returns
                Result.failure(AssistantException(AssistantError.ServerStartBlocked))
            val vm = viewModel()
            vm.uiState.test {
                vm.turnOn(context)
                val state = expectMostRecentItem()
                assertEquals(R.string.entry_error_blocked, state.errorRes)
                assertFalse(state.busy)
            }
            coVerify(exactly = 0) { settings.setEnabled(true) }
        }

    @Test
    fun `other start failures show the generic error and dismiss clears it`() =
        runTest {
            coEvery { starter.ensureRunning(any()) } returns
                Result.failure(AssistantException(AssistantError.ServerStartFailed("x")))
            val vm = viewModel()
            vm.uiState.test {
                vm.turnOn(context)
                assertEquals(R.string.entry_error_start, expectMostRecentItem().errorRes)
                vm.dismissError()
                assertEquals(null, expectMostRecentItem().errorRes)
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
            coVerify(exactly = 1) { starter.ensureRunning(context) }
            config.value = AssistantConfig()
        }

    @Test
    fun `resume while disabled does not start the server`() =
        runTest {
            config.value = AssistantConfig(helperIntroSeen = true)
            viewModel().onResume(context, PermissionSnapshot())
            coVerify(exactly = 0) { starter.ensureRunning(any()) }
            config.value = AssistantConfig()
        }

    @Test
    fun `settings actions are delegated`() =
        runTest {
            val vm = viewModel()
            vm.editor.acceptConsent()
            vm.editor.setVoice(true)
            vm.editor.setSpeech(false)
            vm.editor.saveKey("abc")
            vm.editor.saveKey("  ")
            vm.editor.clearKey()
            vm.turnOff()
            vm.openInput()
            coVerify { settings.acceptPrivacyNotice() }
            coVerify { settings.setVoiceEnabled(true) }
            coVerify { settings.setHelperFlag(HelperFlag.SPEECH_ENABLED, false) }
            coVerify(exactly = 1) { settings.setApiKey(any()) }
            coVerify { settings.clearApiKey() }
            coVerify { settings.setEnabled(false) }
            io.mockk.verify { bubble.openInput() }
        }

    @Test
    fun `key storage failure is reported`() =
        runTest {
            coEvery { settings.setApiKey(any()) } throws java.security.GeneralSecurityException("keystore")
            val vm = viewModel()
            vm.uiState.test {
                vm.editor.saveKey("abc")
                assertEquals(R.string.entry_error_key_store, expectMostRecentItem().errorRes)
            }
        }
}
