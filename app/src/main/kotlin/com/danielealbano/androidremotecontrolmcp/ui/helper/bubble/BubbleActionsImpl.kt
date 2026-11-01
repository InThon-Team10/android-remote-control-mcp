package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.content.Context
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import com.danielealbano.androidremotecontrolmcp.assistant.voice.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Routes the panel's buttons to the assistant session and the live voice conversation. */
internal class BubbleActionsImpl(
    private val session: AssistantSession,
    private val state: BubbleUiState,
    private val liveVoice: LiveVoiceSession,
    private val serverStarter: AssistantServerStarter,
    private val panelRequested: MutableStateFlow<Boolean>,
    private val scope: () -> CoroutineScope?,
    private val context: () -> Context?,
    private val positions: BubblePositionStore,
) : BubbleActions {
    private var voiceJob: Job? = null
    private var lastSent = ""

    override fun onToggleBubble() {
        Log.d(BUBBLE_TRACE, "bubble tapped: panel ${if (panelRequested.value) "closes" else "opens"}")
        panelRequested.value = !panelRequested.value
    }

    override fun onBubbleMoved(permille: Int) {
        Log.d(BUBBLE_TRACE, "bubble moved to $permille/$PERMILLE")
        scope()?.launch { positions.save(permille) }
    }

    override fun onClosePanel() {
        Log.d(BUBBLE_TRACE, "panel closed by the user")
        panelRequested.value = false
    }

    override fun onCancelThinking() {
        Log.d(BUBBLE_TRACE, "thinking cancelled by the user")
        session.stop()
        panelRequested.value = false
        state.setDraft(lastSent)
    }

    override fun onSend(text: String) {
        val host = context() ?: return
        lastSent = text
        state.setDraft("")
        scope()?.launch {
            serverStarter.ensureRunning(host).fold(
                onSuccess = { session.send(text) },
                onFailure = { session.reportError(it.toAssistantError()) },
            )
        }
    }

    override fun onStop() = session.stop()

    override fun onNewChat() = session.newChat()

    override fun onConfirm(
        callId: String,
        allow: Boolean,
    ) = session.answerConfirmation(callId, allow)

    override fun onToggleMic() {
        val host = context() ?: return
        val active = liveVoice.state.value.isActive()
        Log.d(BUBBLE_TRACE, "mic tapped: live voice ${if (active) "stops" else "starts"}")
        voiceJob?.cancel()
        voiceJob =
            scope()?.launch {
                if (active) {
                    liveVoice.stop()
                } else {
                    serverStarter
                        .ensureRunning(host)
                        .fold(
                            onSuccess = { liveVoice.start().exceptionOrNull() },
                            onFailure = { it },
                        )?.let {
                            Log.d(BUBBLE_TRACE, "live voice did not start: ${it.javaClass.simpleName}")
                            session.reportError(it.toAssistantError())
                        }
                }
            }
    }

    fun reset() {
        voiceJob = null
    }
}

private fun Throwable.toAssistantError(): AssistantError =
    (this as? AssistantException)?.error ?: AssistantError.ServerStartFailed(javaClass.simpleName)
