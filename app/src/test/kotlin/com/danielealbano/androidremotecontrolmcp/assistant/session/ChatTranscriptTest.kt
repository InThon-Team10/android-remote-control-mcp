package com.danielealbano.androidremotecontrolmcp.assistant.session

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ChatTranscript")
class ChatTranscriptTest {
    private val transcript = ChatTranscript()

    private fun appendAssistant() = transcript.append { ChatItem.Assistant(it, "a") }

    @Test
    fun `ids are monotonic and not reused after clear`() {
        appendAssistant()
        appendAssistant()
        transcript.clear()
        appendAssistant()

        assertEquals(listOf(3L), transcript.items.value.map { it.id })
    }

    @Test
    fun `update transforms items and preserves ids`() {
        transcript.append { ChatItem.User(it, "u") }
        appendAssistant()

        transcript.update { if (it is ChatItem.Assistant) it.copy(text = "b") else it }

        assertEquals(listOf(1L, 2L), transcript.items.value.map { it.id })
        assertEquals("b", (transcript.items.value[1] as ChatItem.Assistant).text)
        assertEquals("u", (transcript.items.value[0] as ChatItem.User).text)
    }

    @Test
    fun `attention is true for assistant text while hidden and follows visibility`() {
        appendAssistant()
        assertTrue(transcript.attention.value)

        transcript.setChatVisible(true)
        assertFalse(transcript.attention.value)

        transcript.setChatVisible(false)
        assertTrue(transcript.attention.value)
    }

    @Test
    fun `attention follows unanswered confirmation`() {
        transcript.append { ChatItem.Confirmation(it, "c", "t", null) }
        assertTrue(transcript.attention.value)

        transcript.update { if (it is ChatItem.Confirmation) it.copy(answered = true) else it }
        assertFalse(transcript.attention.value)
    }

    @Test
    fun `attention true for error and step limit notice`() {
        transcript.append { ChatItem.Error(it, AssistantError.ApiKeyMissing) }
        assertTrue(transcript.attention.value)

        transcript.append { ChatItem.Notice(it, NoticeKind.StepLimitReached(3)) }
        assertTrue(transcript.attention.value)
    }

    @Test
    fun `attention false for stopped notice user and tool call`() {
        transcript.append { ChatItem.Notice(it, NoticeKind.Stopped) }
        assertFalse(transcript.attention.value)

        transcript.append { ChatItem.User(it, "u") }
        assertFalse(transcript.attention.value)

        transcript.append { ChatItem.ToolCall(it, "c", "t", ToolCallStatus.RUNNING) }
        assertFalse(transcript.attention.value)
    }
}
