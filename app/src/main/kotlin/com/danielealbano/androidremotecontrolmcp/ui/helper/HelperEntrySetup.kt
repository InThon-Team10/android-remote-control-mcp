@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R

@Composable
internal fun SetupBody(
    state: HelperEntryUiState,
    actions: HelperSetupActions,
) {
    val setup = state.setup
    EntryTitle(if (setup.ready) R.string.entry_ready_title else R.string.entry_setup_title)
    EntryText(if (setup.ready) R.string.entry_ready_body else R.string.entry_setup_body)
    state.errorRes?.let { EntryText(it) }
    AccessibilityStep(setup.accessibility, actions)
    NotificationStep(setup.notifications, actions)
    MicrophoneStep(setup.microphone, state.config.voiceEnabled, actions)
    ConsentStep(setup.consent, actions)
    // The built-in key needs nothing from the user, so the AI step only appears for a build without one.
    if (!state.config.builtInApiKeyInUse) AiStep(setup.aiConnected, actions)
}

@Composable
private fun StepCard(
    @StringRes title: Int,
    done: Boolean,
    optional: Boolean = false,
    content: @Composable () -> Unit,
) {
    val status =
        stringResource(
            when {
                done -> R.string.entry_step_done
                optional -> R.string.entry_step_optional
                else -> R.string.entry_step_todo
            },
        )
    EntryCard {
        Column(Modifier.semantics(mergeDescendants = true) { stateDescription = status }) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(status, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun StepButton(
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { EntryText(label) }
}

@Composable
private fun AccessibilityStep(
    done: Boolean,
    actions: HelperSetupActions,
) {
    StepCard(R.string.entry_step_accessibility, done) {
        if (!done) {
            EntryText(R.string.entry_step_accessibility_body)
            StepButton(R.string.entry_open_accessibility, actions::openAccessibilitySettings)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                EntryText(R.string.entry_restricted_hint)
                StepButton(R.string.entry_open_app_info, actions::openAppInfo)
            }
        }
    }
}

@Composable
private fun NotificationStep(
    done: Boolean,
    actions: HelperSetupActions,
) {
    StepCard(R.string.entry_step_notifications, done) {
        if (!done) {
            EntryText(R.string.entry_step_notifications_body)
            StepButton(R.string.entry_allow, actions::requestNotifications)
        }
    }
}

@Composable
private fun MicrophoneStep(
    granted: Boolean,
    voiceEnabled: Boolean,
    actions: HelperSetupActions,
) {
    StepCard(R.string.entry_step_microphone, granted, optional = true) {
        if (granted) {
            EntrySwitch(R.string.entry_voice_toggle, voiceEnabled, actions::setVoice)
        } else {
            EntryText(R.string.entry_step_microphone_body)
            StepButton(R.string.entry_allow, actions::requestMicrophone)
        }
    }
}

@Composable
private fun ConsentStep(
    done: Boolean,
    actions: HelperSetupActions,
) {
    StepCard(R.string.entry_step_consent, done) {
        EntryText(R.string.entry_consent_text)
        if (!done) StepButton(R.string.entry_consent_accept, actions::acceptConsent)
    }
}

@Composable
private fun AiStep(
    connected: Boolean,
    actions: HelperSetupActions,
) {
    StepCard(R.string.entry_step_ai, connected) {
        AiKeyControls(connected, actions)
    }
}

/** Key entry shared by the setup checklist and the settings page. */
@Composable
internal fun AiKeyControls(
    connected: Boolean,
    actions: HelperSetupActions,
) {
    var dialog by rememberSaveable { mutableStateOf(false) }
    if (!connected) EntryText(R.string.entry_step_ai_body)
    StepButton(if (connected) R.string.entry_key_change else R.string.entry_key_enter) { dialog = true }
    if (connected) EntryLink(R.string.entry_key_clear, actions::clearKey)
    if (dialog) {
        KeyDialog(onDismiss = { dialog = false }, onSave = {
            actions.saveKey(it)
            dialog = false
        })
    }
}

@Composable
private fun KeyDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_key_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.entry_key_label)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                )
                TextButton({ visible = !visible }, Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (visible) R.string.entry_key_hide else R.string.entry_key_show))
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(key) }, Modifier.heightIn(min = 48.dp), enabled = key.isNotBlank()) {
                Text(stringResource(R.string.entry_key_save))
            }
        },
        dismissButton = {
            TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.entry_key_cancel)) }
        },
    )
}
