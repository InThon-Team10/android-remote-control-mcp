package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.data.model.HelperFlag
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException

/** Writes the entry flow's choices to [AssistantSettings]; [onKeyStoreFailure] reports a key that was not stored. */
class HelperSettingsEditor(
    private val settings: AssistantSettings,
    private val scope: CoroutineScope,
    private val onKeyStoreFailure: () -> Unit,
) {
    fun setIntroSeen(seen: Boolean) {
        scope.launch { settings.setHelperFlag(HelperFlag.INTRO_SEEN, seen) }
    }

    fun acceptConsent() {
        scope.launch { settings.acceptPrivacyNotice() }
    }

    fun setVoice(enabled: Boolean) {
        scope.launch { settings.setVoiceEnabled(enabled) }
    }

    fun setSpeech(enabled: Boolean) {
        scope.launch { settings.setHelperFlag(HelperFlag.SPEECH_ENABLED, enabled) }
    }

    fun clearKey() {
        scope.launch { settings.clearApiKey() }
    }

    fun saveKey(key: String) {
        if (key.isBlank()) return
        scope.launch {
            try {
                settings.setApiKey(key)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "API key rejected", e)
            } catch (e: GeneralSecurityException) {
                Logger.w(TAG, "API key could not be stored", e)
                onKeyStoreFailure()
            }
        }
    }

    private companion object {
        const val TAG = "MCP:HelperSettings"
    }
}
