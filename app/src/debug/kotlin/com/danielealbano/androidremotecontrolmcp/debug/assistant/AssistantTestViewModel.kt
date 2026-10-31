package com.danielealbano.androidremotecontrolmcp.debug.assistant

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Debug-only state holder for [AssistantTestActivity]; exercises the headless assistant. */
@HiltViewModel
class AssistantTestViewModel
    @Inject
    constructor(
        private val settings: AssistantSettings,
        val session: AssistantSession,
        private val serverStarter: AssistantServerStarter,
        private val liveVoice: LiveVoiceSession,
    ) : ViewModel() {
        val config: StateFlow<AssistantConfig> =
            settings.config.stateIn(viewModelScope, SharingStarted.Eagerly, AssistantConfig())
        val liveState: StateFlow<LiveVoiceState> = liveVoice.state
        private val _message = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = _message.asStateFlow()

        /** A blank [key] clears the stored key. */
        fun saveApiKey(key: String) = launchSetting { if (key.isBlank()) settings.clearApiKey() else settings.setApiKey(key) }

        fun setEnabled(
            enabled: Boolean,
            privacyAccepted: Boolean,
        ) = launchSetting {
            require(!enabled || privacyAccepted) { "Accept the privacy notice first" }
            if (enabled) settings.acceptPrivacyNotice()
            settings.setEnabled(enabled)
        }

        fun setVoiceEnabled(enabled: Boolean) = launchSetting { settings.setVoiceEnabled(enabled) }

        fun setModel(model: String) = launchSetting { settings.setModel(model) }

        fun setStepLimit(text: String) =
            launchSetting {
                val limit = requireNotNull(text.toIntOrNull()) { "Step limit must be a number" }
                settings.setStepLimit(AssistantConfig.validateStepLimit(limit).getOrThrow())
            }

        /** [activity] is only used during the call (the server starter needs an Activity context, D8). */
        fun send(
            activity: Activity,
            text: String,
        ) {
            viewModelScope.launch {
                serverStarter.ensureRunning(activity).fold(
                    onSuccess = { session.send(text) },
                    onFailure = { session.reportError(it.toAssistantError()) },
                )
            }
        }

        /** Starts the live voice conversation; [activity] is only used during the call (server start, D8). */
        fun startTalk(activity: Activity) {
            viewModelScope.launch {
                serverStarter
                    .ensureRunning(activity)
                    .fold(
                        onSuccess = { liveVoice.start().exceptionOrNull() },
                        onFailure = { it },
                    )?.let { session.reportError(it.toAssistantError()) }
            }
        }

        fun endTalk() {
            viewModelScope.launch { liveVoice.stop() }
        }

        private fun launchSetting(block: suspend () -> Unit) {
            viewModelScope.launch { _message.value = runCatching { block() }.exceptionOrNull()?.message }
        }
    }

private fun Throwable.toAssistantError(): AssistantError =
    (this as? AssistantException)?.error ?: AssistantError.ServerStartFailed(javaClass.simpleName)
