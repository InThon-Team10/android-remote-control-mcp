package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Entry lifecycle for text guidance; dictation belongs to the request panel. */
interface HelperConversationControl {
    /** No background microphone conversation is active in text mode. */
    val active: Flow<Boolean>

    suspend fun start(): Result<Unit>

    suspend fun stop()
}

class TextHelperConversationControl
    @Inject
    constructor(
        private val session: AssistantSession,
    ) : HelperConversationControl {
        override val active: Flow<Boolean> = flowOf(false)

        override suspend fun start(): Result<Unit> = Result.success(Unit)

        override suspend fun stop() = session.stop()
    }
