package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What the entry screens need from the live conversation, and nothing more. The provider stream behind
 * [LiveVoiceSession] implements it by exposing its connecting/listening/speaking states through the session.
 */
interface HelperConversationControl {
    /** True while a conversation holds the microphone (connecting, listening or speaking). */
    val active: Flow<Boolean>

    /** Idempotent while running; the failure is a typed `AssistantException`. */
    suspend fun start(): Result<Unit>

    /** Ends the conversation and releases the microphone; idempotent. */
    suspend fun stop()
}

class LiveHelperConversationControl
    @Inject
    constructor(
        private val session: LiveVoiceSession,
    ) : HelperConversationControl {
        override val active: Flow<Boolean> = session.state.map { it.isActive() }.distinctUntilChanged()

        override suspend fun start(): Result<Unit> = session.start()

        override suspend fun stop() = session.stop()
    }
