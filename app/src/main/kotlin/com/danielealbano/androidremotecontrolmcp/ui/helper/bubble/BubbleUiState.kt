package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Compose windows render; written by the host on the main thread. */
internal class BubbleUiState {
    private val _layout = MutableStateFlow(BubbleLayoutState(panelOpen = false, bubbleVisible = true))
    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _session = MutableStateFlow<SessionState>(SessionState.Idle)
    private val _voiceEnabled = MutableStateFlow(false)
    private val _listening = MutableStateFlow(false)
    private val _busy = MutableStateFlow(false)
    private val _draft = MutableStateFlow("")

    val layout: StateFlow<BubbleLayoutState> = _layout.asStateFlow()
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** The mic button is shown only while voice is enabled in the settings. */
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    /** True while a tool call runs or a typed request awaits its answer ([bubbleBusy]). */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The request being typed; lives here so it survives collapsing the panel. */
    val draft: StateFlow<String> = _draft.asStateFlow()

    fun setLayout(value: BubbleLayoutState) {
        _layout.value = value
    }

    fun setItems(value: List<ChatItem>) {
        _items.value = value
    }

    fun setSession(value: SessionState) {
        _session.value = value
    }

    fun setVoiceEnabled(value: Boolean) {
        _voiceEnabled.value = value
    }

    fun setListening(value: Boolean) {
        _listening.value = value
    }

    fun setBusy(value: Boolean) {
        _busy.value = value
    }

    fun setDraft(value: String) {
        _draft.value = value
    }
}
