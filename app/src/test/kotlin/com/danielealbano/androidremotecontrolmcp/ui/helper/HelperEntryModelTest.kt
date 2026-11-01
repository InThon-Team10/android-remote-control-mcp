package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("HelperEntryModel")
class HelperEntryModelTest {
    private val readyConfig = AssistantConfig(privacyNoticeAccepted = true, apiKeyConfigured = true)
    private val running = ServerStatus.Running(8080, "127.0.0.1", false)

    private fun ready(
        server: ServerStatus = ServerStatus.Stopped,
        enabled: Boolean = false,
        busy: Boolean = false,
        errorRes: Int? = null,
    ) = HelperEntryUiState(
        loaded = true,
        config = readyConfig.copy(enabled = enabled),
        accessibilityReady = true,
        server = server,
        busy = busy,
        errorRes = errorRes,
    )

    private fun at(page: HelperEntryPage) = HelperEntryModel(page)

    @Test
    fun `first page is welcome until the intro was seen then home`() {
        assertEquals(HelperEntryPage.WELCOME, HelperEntryModel.initial(introSeen = false).page)
        assertEquals(HelperEntryPage.HOME, HelperEntryModel.initial(introSeen = true).page)
    }

    @Test
    fun `practice question ignores spacing and punctuation`() {
        val expected = "화면 설정은 어디에 있어요?"
        assertTrue(practiceQuestionMatches("화면 설정은 어디에 있어요?!", expected))
        assertTrue(practiceQuestionMatches(expected, expected))
        assertTrue(practiceQuestionMatches("  화면설정은어디에있어요  ", expected))
    }

    @Test
    fun `practice question rejects empty and partial drafts`() {
        val expected = "화면 설정은 어디에 있어요?"
        assertFalse(practiceQuestionMatches("", expected))
        assertFalse(practiceQuestionMatches("   ", expected))
        assertFalse(practiceQuestionMatches("화면 설정은", expected))
        assertFalse(practiceQuestionMatches("anything", ""))
    }

    @Test
    fun `practice question rejects a different sentence`() {
        assertFalse(practiceQuestionMatches("배터리는 어디에 있어요?", "화면 설정은 어디에 있어요?"))
    }

    @Test
    fun `practice question is case insensitive`() {
        assertTrue(practiceQuestionMatches("WI-FI?", "wi fi"))
    }

    @Test
    fun `setup asks consent first even when permissions exist`() {
        val page = helperSetupPage(consented = false, accessible = true, microphoneDecided = true)
        assertEquals(HelperEntryPage.CONSENT, page)
    }

    @Test
    fun `setup asks accessibility after consent`() {
        assertEquals(HelperEntryPage.PERMISSION, helperSetupPage(true, accessible = false, microphoneDecided = false))
    }

    @Test
    fun `setup asks the optional microphone after accessibility`() {
        assertEquals(HelperEntryPage.MICROPHONE, helperSetupPage(true, true, microphoneDecided = false))
    }

    @Test
    fun `completed setup goes home`() {
        assertEquals(HelperEntryPage.HOME, helperSetupPage(true, true, true))
    }

    @Test
    fun `advance outside practice changes nothing`() {
        listOf(
            HelperEntryPage.WELCOME,
            HelperEntryPage.INVITE,
            HelperEntryPage.CONSENT,
            HelperEntryPage.HOME,
        ).forEach { assertEquals(at(it), at(it).advance()) }
    }

    @Test
    fun `practice runs open ask follow done then invite`() {
        var model = at(HelperEntryPage.WELCOME).practice()
        assertEquals(HelperEntryPage.PRACTICE to HelperPracticeStep.OPEN, model.page to model.step)
        model = model.advance()
        assertEquals(HelperPracticeStep.ASK, model.step)
        model = model.advance()
        assertEquals(HelperPracticeStep.FOLLOW, model.step)
        model = model.advance()
        assertEquals(HelperPracticeStep.DONE, model.step)
        assertEquals(HelperEntryPage.PRACTICE, model.page)
        assertEquals(HelperEntryPage.INVITE, model.advance().page)
    }

    @Test
    fun `replaying practice starts fresh and invite does not grant anything`() {
        val late = HelperEntryModel(HelperEntryPage.INVITE, HelperPracticeStep.DONE)
        assertEquals(HelperEntryModel(HelperEntryPage.PRACTICE, HelperPracticeStep.OPEN), late.practice())
        assertEquals(late, late.advance())
    }

    @Test
    fun `back and skip go home from every non root page`() {
        listOf(
            HelperEntryPage.PRACTICE,
            HelperEntryPage.INVITE,
            HelperEntryPage.CONSENT,
            HelperEntryPage.PERMISSION,
            HelperEntryPage.MICROPHONE,
            HelperEntryPage.SETTINGS,
        ).forEach {
            assertEquals(HelperEntryPage.HOME, at(it).back().page)
            assertEquals(HelperEntryPage.HOME, at(it).skip().page)
        }
    }

    @Test
    fun `system back exits only from welcome and home`() {
        assertTrue(at(HelperEntryPage.WELCOME).exitsOnBack)
        assertTrue(at(HelperEntryPage.HOME).exitsOnBack)
        HelperEntryPage.entries
            .filter { it != HelperEntryPage.WELCOME && it != HelperEntryPage.HOME }
            .forEach { assertFalse(at(it).exitsOnBack) }
        assertEquals(at(HelperEntryPage.HOME), at(HelperEntryPage.HOME).back())
    }

    @Test
    fun `settings intro and page transitions`() {
        assertEquals(HelperEntryPage.SETTINGS, at(HelperEntryPage.HOME).settings().page)
        assertEquals(HelperEntryPage.WELCOME, at(HelperEntryPage.SETTINGS).intro().page)
        assertEquals(HelperEntryPage.MICROPHONE, at(HelperEntryPage.WELCOME).page(HelperEntryPage.MICROPHONE).page)
    }

    @Test
    fun `practice coach text follows the step`() {
        val titles = HelperPracticeStep.entries.map { practiceCoach(it).first }
        assertEquals(HelperPracticeStep.entries.size, titles.toSet().size)
        val ask = practiceCoach(HelperPracticeStep.ASK)
        assertEquals(R.string.helper_tour_ask_title to R.string.helper_tour_ask_body, ask)
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
    fun `home status is missing until setup is ready and the action starts setup`() {
        val state = HelperEntryUiState(loaded = true)
        assertEquals(HomeStatus.MISSING, state.homeStatus)
        assertEquals(HomeAction.START, state.homeAction)
    }

    @Test
    fun `home status is on only when enabled and the server runs`() {
        assertEquals(HomeStatus.ON, ready(running, enabled = true).homeStatus)
        assertEquals(HomeAction.OPEN, ready(running, enabled = true).homeAction)
        assertEquals(HomeStatus.OFF, ready(running, enabled = false).homeStatus)
        assertEquals(HomeStatus.OFF, ready(ServerStatus.Stopped, enabled = true).homeStatus)
        assertEquals(HomeAction.START, ready().homeAction)
    }

    @Test
    fun `home status follows starting and failures`() {
        assertEquals(HomeStatus.STARTING, ready(ServerStatus.Starting).homeStatus)
        assertEquals(HomeStatus.STARTING, ready(busy = true).homeStatus)
        assertEquals(HomeAction.STARTING, ready(busy = true).homeAction)
        assertEquals(HomeStatus.FAILED, ready(ServerStatus.Error("x")).homeStatus)
        assertEquals(HomeAction.RETRY, ready(errorRes = 1).homeAction)
    }

    @Test
    fun `home status visual maps every status`() {
        val on = homeStatusVisual(HomeStatus.ON, true)
        assertTrue(on.awake)
        assertEquals(R.string.helper_home_active_title, on.titleRes)
        assertNull(on.bodyRes)
        val off = homeStatusVisual(HomeStatus.OFF, true)
        assertFalse(off.awake)
        assertEquals(R.string.helper_home_inactive_title, off.titleRes)
        assertNull(off.bodyRes)
        assertEquals(R.string.entry_body_missing, homeStatusVisual(HomeStatus.MISSING, true).bodyRes)
        assertEquals(R.string.helper_home_ai_missing, homeStatusVisual(HomeStatus.MISSING, false).bodyRes)
        assertEquals(R.string.entry_body_starting, homeStatusVisual(HomeStatus.STARTING, true).bodyRes)
        assertEquals(R.string.entry_body_failed, homeStatusVisual(HomeStatus.FAILED, true).bodyRes)
        assertFalse(HomeStatus.entries.filter { it != HomeStatus.ON }.any { homeStatusVisual(it, true).awake })
    }

    @Test
    fun `practice mic is disabled during a conversation`() {
        assertFalse(practiceMicEnabled(conversationActive = true))
        assertTrue(practiceMicEnabled(conversationActive = false))
    }
}
