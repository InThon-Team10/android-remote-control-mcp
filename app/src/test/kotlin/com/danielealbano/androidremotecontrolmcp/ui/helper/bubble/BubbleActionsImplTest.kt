package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
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
    private val actions =
        BubbleActionsImpl(
            session,
            state,
            mockk<LiveVoiceSession>(relaxed = true),
            starter,
            panelRequested,
            { scope },
            { context },
            mockk<BubblePositionStore>(relaxed = true),
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
}
