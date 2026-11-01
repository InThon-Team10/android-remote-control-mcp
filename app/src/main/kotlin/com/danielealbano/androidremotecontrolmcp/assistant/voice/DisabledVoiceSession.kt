package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** No Realtime connection, microphone capture or playback in the text-only build. */
class DisabledVoiceSession
    @Inject
    constructor() : LiveVoiceSession {
        override val state: StateFlow<LiveVoiceState> = MutableStateFlow(LiveVoiceState.Idle)

        override suspend fun start(): Result<Unit> = Result.failure(AssistantException(AssistantError.VoiceDisabled))

        override suspend fun stop() = Unit

        override fun sendText(text: String) = Unit
    }
