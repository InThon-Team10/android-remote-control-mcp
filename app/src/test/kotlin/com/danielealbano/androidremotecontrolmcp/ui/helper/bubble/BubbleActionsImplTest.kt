package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.ui.helper.practice.PracticeDictation
import com.danielealbano.androidremotecontrolmcp.ui.helper.practice.PracticeDictationState
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BubbleActionsImplTest {
    private val session = mockk<AssistantSession>(relaxed = true)
    private val state = BubbleUiState()
    private val panelRequested = MutableStateFlow(true)
    private val context = mockk<Context>()
    private val starter =
        mockk<AssistantServerStarter> { coEvery { ensureRunning(context) } returns Result.success(Unit) }
    private val scope = TestScope(UnconfinedTestDispatcher())
    private lateinit var onTranscript: (String) -> Unit
    private val voiceState = MutableStateFlow(PracticeDictationState())
    private val dictation =
        object : PracticeDictation {
            override val state = voiceState
            override val available = true

            override fun start(draft: String) {
                voiceState.value = PracticeDictationState(listening = true, recognizing = true)
            }

            override fun stop() {
                voiceState.value = PracticeDictationState(recognizing = true)
            }

            override fun cancel() {
                voiceState.value = PracticeDictationState()
            }
        }
    private val actions =
        BubbleActionsImpl(
            session,
            state,
            starter,
            panelRequested,
            { scope },
            { context },
            mockk<BubblePositionStore>(relaxed = true),
            { _, callback ->
                onTranscript = callback
                dictation
            },
        )

    @Test
    fun `cancelling thinking stops the run, closes the panel and restores the request`() {
        state.setDraft("where is wifi")
        actions.onSend("where is wifi")
        assertEquals("", state.draft.value)

        actions.onCancelThinking()

        verify { session.stop() }
        assertFalse(panelRequested.value)
        assertEquals("where is wifi", state.draft.value)
    }

    @Test
    fun `dictation only edits the draft and sends text after explicit submission`() {
        actions.onToggleMic()
        onTranscript("wifi settings")
        assertEquals("wifi settings", state.draft.value)
        actions.onSend(state.draft.value)
        verify(exactly = 0) { session.send(any()) }

        actions.onToggleMic()
        actions.onSend(state.draft.value)
        verify(exactly = 0) { session.send(any()) }
        onTranscript("wifi please")
        voiceState.value = PracticeDictationState()
        actions.onSend(state.draft.value)
        verify(exactly = 1) { session.send("wifi please") }
    }

    @Test
    fun `closing the panel cancels dictation and preserves the draft`() {
        actions.onToggleMic()
        onTranscript("wifi")
        actions.onClosePanel()
        assertFalse(voiceState.value.busy)
        assertEquals("wifi", state.draft.value)
        verify(exactly = 0) { session.send(any()) }
    }

    @Test
    fun `server failure restores the typed request`() {
        coEvery { starter.ensureRunning(context) } returns Result.failure(IllegalStateException())
        actions.onSend("wifi")
        assertEquals("wifi", state.draft.value)
        verify(exactly = 0) { session.send(any()) }
        verify { session.reportError(any()) }
    }
}
