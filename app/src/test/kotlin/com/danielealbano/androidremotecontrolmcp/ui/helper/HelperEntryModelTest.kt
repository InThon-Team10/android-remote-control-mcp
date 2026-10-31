package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HelperEntryModel")
class HelperEntryModelTest {
    private val readyConfig = AssistantConfig(privacyNoticeAccepted = true, apiKeyConfigured = true)

    private fun ready(
        bubble: HelperBubbleStatus = HelperBubbleStatus.OFF,
        busy: Boolean = false,
        errorRes: Int? = null,
    ) = HelperEntryUiState(
        loaded = true,
        config = readyConfig,
        accessibilityReady = true,
        bubble = bubble,
        busy = busy,
        errorRes = errorRes,
    )

    @Test
    fun `first page is welcome until the intro was seen then home`() {
        assertEquals(HelperEntryPage.WELCOME, HelperEntryModel.initial(introSeen = false).page)
        assertEquals(HelperEntryPage.HOME, HelperEntryModel.initial(introSeen = true).page)
    }

    @Test
    fun `help returns to the page it was opened from`() {
        assertEquals(HelperEntryPage.WELCOME, HelperEntryModel(HelperEntryPage.WELCOME).help().back().page)
        assertEquals(HelperEntryPage.HOME, HelperEntryModel(HelperEntryPage.HOME).help().back().page)
    }

    @Test
    fun `help opened twice keeps the original return page`() {
        val help = HelperEntryModel(HelperEntryPage.HOME).help()
        assertEquals(help, help.help())
    }

    @Test
    fun `back from setup and settings goes home`() {
        assertEquals(HelperEntryPage.HOME, HelperEntryModel(HelperEntryPage.SETUP).back().page)
        assertEquals(HelperEntryPage.HOME, HelperEntryModel(HelperEntryPage.SETTINGS).back().page)
    }

    @Test
    fun `system back exits only from welcome and home`() {
        assertTrue(HelperEntryModel(HelperEntryPage.WELCOME).exitsOnBack)
        assertTrue(HelperEntryModel(HelperEntryPage.HOME).exitsOnBack)
        assertFalse(HelperEntryModel(HelperEntryPage.SETUP).exitsOnBack)
        assertFalse(HelperEntryModel(HelperEntryPage.HELP).exitsOnBack)
        assertFalse(HelperEntryModel(HelperEntryPage.SETTINGS).exitsOnBack)
        val home = HelperEntryModel(HelperEntryPage.HOME)
        assertEquals(home, home.back())
    }

    @Test
    fun `settings and intro transitions`() {
        assertEquals(HelperEntryPage.SETTINGS, HelperEntryModel(HelperEntryPage.HOME).settings().page)
        assertEquals(HelperEntryPage.WELCOME, HelperEntryModel(HelperEntryPage.SETTINGS).intro().page)
        assertEquals(HelperEntryPage.SETUP, HelperEntryModel(HelperEntryPage.WELCOME).setup().page)
    }

    @Test
    fun `setup is ready with accessibility consent and key only`() {
        val checklist =
            SetupChecklist(
                accessibility = true,
                notifications = false,
                microphone = false,
                consent = true,
                aiConnected = true,
            )
        assertTrue(checklist.ready)
        assertFalse(checklist.copy(accessibility = false).ready)
        assertFalse(checklist.copy(consent = false).ready)
        assertFalse(checklist.copy(aiConnected = false).ready)
    }

    @Test
    fun `built-in key counts as connected through apiKeyConfigured`() {
        val state = ready().copy(config = readyConfig.copy(builtInApiKeyInUse = true))
        assertTrue(state.setup.aiConnected)
    }

    @Test
    fun `home status is missing until setup is ready`() {
        val state = HelperEntryUiState(loaded = true)
        assertEquals(HomeStatus.MISSING, state.homeStatus)
        assertEquals(HomeAction.CONTINUE_SETUP, state.homeAction)
    }

    @Test
    fun `home status follows the bubble`() {
        assertEquals(HomeAction.START, ready().homeAction)
        assertEquals(HomeStatus.STARTING, ready(HelperBubbleStatus.STARTING).homeStatus)
        assertEquals(HomeAction.STARTING, ready(HelperBubbleStatus.STARTING).homeAction)
        assertEquals(HomeAction.OPEN, ready(HelperBubbleStatus.ON).homeAction)
        assertEquals(HomeAction.RETRY, ready(HelperBubbleStatus.FAILED).homeAction)
    }

    @Test
    fun `busy start shows starting and a start error shows retry`() {
        assertEquals(HomeStatus.STARTING, ready(busy = true).homeStatus)
        assertEquals(HomeAction.RETRY, ready(errorRes = 1).homeAction)
    }
}
