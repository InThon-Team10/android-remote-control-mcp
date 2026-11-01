@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.danielealbano.androidremotecontrolmcp.R

@Composable
internal fun SettingsBody(
    state: HelperEntryUiState,
    setup: HelperSetupActions,
    actions: HelperSettingsActions,
) {
    EntryTitle(R.string.entry_settings_title, compact = true)
    EntrySwitch(R.string.entry_speech_toggle, state.config.helperSpeechEnabled, actions::setSpeech)
    if (state.permissions.microphone) {
        EntrySwitch(R.string.entry_voice_toggle, state.config.voiceEnabled, setup::setVoice)
    } else {
        StepCard(R.string.entry_step_microphone) {
            EntryText(R.string.entry_step_microphone_body)
            EntryAction(R.string.entry_allow, setup::requestMicrophone)
        }
    }
    if (!state.permissions.notifications) {
        StepCard(R.string.entry_step_notifications) {
            EntryText(R.string.entry_step_notifications_body)
            EntryAction(R.string.entry_allow, setup::requestNotifications)
        }
    }
    EntryLink(R.string.entry_reset, actions::showIntroAgain)
}

/** An optional permission that is still open, read by a screen reader as "title, optional". */
@Composable
private fun StepCard(
    @StringRes title: Int,
    content: @Composable () -> Unit,
) {
    val status = stringResource(R.string.entry_step_optional)
    EntryCard {
        Column(Modifier.semantics(mergeDescendants = true) { stateDescription = status }) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}
