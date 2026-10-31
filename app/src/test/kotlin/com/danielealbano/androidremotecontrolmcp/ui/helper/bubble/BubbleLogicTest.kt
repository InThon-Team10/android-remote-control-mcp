package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.NoticeKind
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BubbleLogicTest {
    private val ready = AssistantConfig(enabled = true, privacyNoticeAccepted = true, apiKeyConfigured = true)

    @Test
    fun `prerequisites need enabled, privacy notice and key`() {
        assertTrue(bubblePrerequisitesMet(ready))
        assertFalse(bubblePrerequisitesMet(ready.copy(enabled = false)))
        assertFalse(bubblePrerequisitesMet(ready.copy(privacyNoticeAccepted = false)))
        assertFalse(bubblePrerequisitesMet(ready.copy(apiKeyConfigured = false)))
    }

    @Test
    fun `send needs a non blank draft and no run`() {
        assertTrue(sendEnabled("hi", running = false))
        assertFalse(sendEnabled("  ", running = false))
        assertFalse(sendEnabled("", running = false))
        assertFalse(sendEnabled("hi", running = true))
    }

    @Test
    fun `a window is interactive only when shown and neither touch through nor suppressed`() {
        assertTrue(windowInteractive(shown = true, touchThrough = false, suppressed = false))
        assertFalse(windowInteractive(shown = false, touchThrough = false, suppressed = false))
        assertFalse(windowInteractive(shown = true, touchThrough = true, suppressed = false))
        assertFalse(windowInteractive(shown = true, touchThrough = false, suppressed = true))
    }

    @Test
    fun `latest assistant text after the last user request is shown`() {
        val items =
            listOf(
                ChatItem.Assistant(1, "old"),
                ChatItem.User(2, "q"),
                ChatItem.Assistant(3, "a1"),
                ChatItem.Assistant(4, "a2"),
            )

        assertEquals(PanelMessage.Answer("a2"), panelMessage(items))
    }

    @Test
    fun `old answer is cleared once a new request is sent`() {
        val items = listOf(ChatItem.Assistant(1, "old"), ChatItem.User(2, "q"))

        assertEquals(PanelMessage.None, panelMessage(items))
    }

    @Test
    fun `error and notice map to short messages`() {
        val error = listOf(ChatItem.Error(1, AssistantError.ModelQuotaExceeded))
        val notice = listOf(ChatItem.Notice(1, NoticeKind.Stopped))

        assertEquals(PanelMessage.Problem(R.string.bubble_error_quota), panelMessage(error))
        assertEquals(PanelMessage.Problem(R.string.bubble_notice_stopped), panelMessage(notice))
    }

    @Test
    fun `pending confirmation wins over an answer`() {
        val items = listOf(ChatItem.Confirmation(1, "c1", "close_app", null), ChatItem.Assistant(2, "a"))

        assertEquals(PanelMessage.Confirm("c1", "close_app"), panelMessage(items))
    }

    @Test
    fun `answered confirmation is not pending`() {
        val items = listOf(ChatItem.Confirmation(1, "c1", "close_app", true), ChatItem.Assistant(2, "a"))

        assertEquals(PanelMessage.Answer("a"), panelMessage(items))
    }

    @Test
    fun `only assistant text newer than the last spoken id is returned`() {
        val items = listOf(ChatItem.Assistant(1, "a"), ChatItem.Assistant(3, "b"))

        assertEquals(3L to "b", newAssistantText(items, 1))
        assertNull(newAssistantText(items, 3))
        assertEquals(3L, latestAssistantId(items))
        assertEquals(-1L, latestAssistantId(emptyList()))
    }

    @Test
    fun `guidance hides the bubble and collapses the panel`() {
        assertEquals(BubbleLayoutState(panelOpen = false, bubbleVisible = false), resolveLayout(true, true))
        assertEquals(BubbleLayoutState(panelOpen = true, bubbleVisible = true), resolveLayout(true, false))
        assertEquals(BubbleLayoutState(panelOpen = false, bubbleVisible = true), resolveLayout(false, false))
    }
}
