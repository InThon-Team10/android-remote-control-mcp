package com.danielealbano.androidremotecontrolmcp.assistant.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe transcript that also owns the chat visibility and the derived "needs attention" flag
 * (chat hidden and the last item asks for the user). Ids are never reused, also after [clear].
 */
internal class ChatTranscript {
    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    private val _chatVisible = MutableStateFlow(false)
    private val _attention = MutableStateFlow(false)
    private var nextId = 1L

    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()
    val chatVisible: StateFlow<Boolean> = _chatVisible.asStateFlow()
    val attention: StateFlow<Boolean> = _attention.asStateFlow()

    @Synchronized
    fun setChatVisible(visible: Boolean) {
        _chatVisible.value = visible
        refresh()
    }

    @Synchronized
    fun append(create: (Long) -> ChatItem) {
        _items.value = _items.value + create(nextId++)
        refresh()
    }

    /** Applies [transform] to every item; transforms MUST preserve the item id. */
    @Synchronized
    fun update(transform: (ChatItem) -> ChatItem) {
        _items.value = _items.value.map(transform)
        refresh()
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        refresh()
    }

    private fun refresh() {
        val needs =
            when (val last = _items.value.lastOrNull()) {
                is ChatItem.Assistant, is ChatItem.Error -> true
                is ChatItem.Notice -> last.kind !is NoticeKind.Stopped
                is ChatItem.Confirmation -> last.answered == null
                else -> false
            }
        _attention.value = !_chatVisible.value && needs
    }
}
