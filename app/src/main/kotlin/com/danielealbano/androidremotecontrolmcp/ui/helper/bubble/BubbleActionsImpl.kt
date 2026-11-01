package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.content.Context
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import com.danielealbano.androidremotecontrolmcp.ui.helper.practice.OnDevicePracticeDictation
import com.danielealbano.androidremotecontrolmcp.ui.helper.practice.PracticeDictation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Routes the panel's buttons to the assistant session and on-device dictation. */
internal class BubbleActionsImpl(
    private val session: AssistantSession,
    private val state: BubbleUiState,
    private val serverStarter: AssistantServerStarter,
    private val panelRequested: MutableStateFlow<Boolean>,
    private val scope: () -> CoroutineScope?,
    private val context: () -> Context?,
    private val positions: BubblePositionStore,
    private val dictationFactory: (Context, (String) -> Unit) -> PracticeDictation =
        { host, onText -> OnDevicePracticeDictation(host, onText) },
) : BubbleActions {
    private var voiceJob: Job? = null
    private var dictation: PracticeDictation? = null
    private var lastSent = ""

    override fun onToggleBubble() {
        Log.d(BUBBLE_TRACE, "bubble tapped: panel ${if (panelRequested.value) "closes" else "opens"}")
        if (panelRequested.value) cancelDictation()
        panelRequested.value = !panelRequested.value
    }

    override fun onBubbleMoved(permille: Int) {
        Log.d(BUBBLE_TRACE, "bubble moved to $permille/$PERMILLE")
        scope()?.launch { positions.save(permille) }
    }

    override fun onClosePanel() {
        cancelDictation()
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
        if (!sendEnabled(text, state.session.value != SessionState.Idle)) return
        if (dictation?.state?.value?.busy == true) return
        val host = context() ?: return
        cancelDictation()
        lastSent = text
        state.setDraft("")
        scope()?.launch {
            serverStarter.ensureRunning(host).fold(
                onSuccess = { session.send(text) },
                onFailure = {
                    state.setDraft(text)
                    session.reportError(it.toAssistantError())
                },
            )
        }
    }

    override fun onStop() {
        cancelDictation()
        session.stop()
    }

    override fun onNewChat() {
        cancelDictation()
        session.newChat()
    }

    override fun onConfirm(
        callId: String,
        allow: Boolean,
    ) = session.answerConfirmation(callId, allow)

    override fun onToggleMic() {
        val host = context() ?: return
        if (state.busy.value || state.session.value != SessionState.Idle) return
        val current =
            dictation ?: dictationFactory(host, state::setDraft).also { created ->
                dictation = created
                voiceJob = scope()?.launch { created.state.collect(state::setDictation) }
            }
        if (current.state.value.listening) {
            current.stop()
        } else if (!current.state.value.recognizing) {
            current.start(state.draft.value)
        }
    }

    fun cancelDictation() {
        dictation?.cancel()
    }

    fun reset() {
        cancelDictation()
        voiceJob?.cancel()
        voiceJob = null
        dictation = null
    }
}

private fun Throwable.toAssistantError(): AssistantError =
    (this as? AssistantException)?.error ?: AssistantError.ServerStartFailed(javaClass.simpleName)
