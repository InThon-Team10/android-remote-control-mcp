package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.data.model.HelperFlag
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Writes the entry flow's simple choices to [AssistantSettings]. */
class HelperSettingsEditor(
    private val settings: AssistantSettings,
    private val scope: CoroutineScope,
) {
    fun setIntroSeen(seen: Boolean) {
        scope.launch { settings.setHelperFlag(HelperFlag.INTRO_SEEN, seen) }
    }

    fun setVoice(enabled: Boolean) {
        scope.launch { settings.setVoiceEnabled(enabled) }
    }

    fun setSpeech(enabled: Boolean) {
        scope.launch { settings.setHelperFlag(HelperFlag.SPEECH_ENABLED, enabled) }
    }
}
