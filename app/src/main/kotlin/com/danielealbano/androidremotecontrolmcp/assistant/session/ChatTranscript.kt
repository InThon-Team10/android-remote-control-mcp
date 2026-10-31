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
    private var liveId: Long? = null
    private var liveUser = false

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
        liveId = null
        _items.value = _items.value + create(nextId++)
        refresh()
    }

    /** Appends [text] to the open live item of the same side, or opens a new one (closed by any other append). */
    @Synchronized
    fun appendLive(
        user: Boolean,
        text: String,
    ) {
        val open = liveId
        if (open != null && liveUser == user) {
            update { item ->
                when {
                    item.id != open -> item
                    item is ChatItem.User -> item.copy(text = item.text + text)
                    item is ChatItem.Assistant -> item.copy(text = item.text + text)
                    else -> item
                }
            }
            return
        }
        append { id -> if (user) ChatItem.User(id, text) else ChatItem.Assistant(id, text) }
        liveId = _items.value.last().id
        liveUser = user
    }

    /** Closes the open live item so the next chunk starts a new one. */
    @Synchronized
    fun endLiveTurn() {
        liveId = null
    }

    /** Applies [transform] to every item; transforms MUST preserve the item id. */
    @Synchronized
    fun update(transform: (ChatItem) -> ChatItem) {
        _items.value = _items.value.map(transform)
        refresh()
    }

    @Synchronized
    fun clear() {
        liveId = null
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
