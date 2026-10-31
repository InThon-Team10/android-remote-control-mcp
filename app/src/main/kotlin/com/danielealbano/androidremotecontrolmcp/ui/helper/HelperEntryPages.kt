@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R

@Composable
internal fun WelcomeBody(help: () -> Unit) {
    EntryTitle(R.string.entry_welcome_title)
    EntryText(R.string.entry_welcome_body)
    UsageExample()
    EntryText(R.string.entry_bubble_explanation)
    EntryLink(R.string.entry_help, help)
}

@Composable
internal fun HelpBody() {
    EntryTitle(R.string.entry_how_title)
    UsageExample()
    HowToUse()
    EntryText(R.string.entry_help_return)
}

@Composable
private fun UsageExample() {
    EntryCard {
        EntryText(R.string.entry_example)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text(
                stringResource(R.string.entry_search),
                Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { EntryText(R.string.entry_caption) }
        }
        Surface(
            modifier = Modifier.align(Alignment.End),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(
                stringResource(R.string.entry_name),
                Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
internal fun HomeBody(
    state: HelperEntryUiState,
    turnOff: () -> Unit,
    help: () -> Unit,
) {
    EntryTitle(R.string.entry_home_title)
    EntryCard {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(statusTitle(state.homeStatus)), style = MaterialTheme.typography.titleLarge)
            EntryText(statusBody(state.homeStatus))
            state.errorRes?.let { EntryText(it) }
        }
        EntryText(if (state.serverRunning) R.string.entry_server_on else R.string.entry_server_off)
        if (!state.accessibilityReady) EntryText(R.string.entry_accessibility_lost)
        if (state.config.enabled || state.bubble == HelperBubbleStatus.ON) EntryLink(R.string.entry_stop, turnOff)
    }
    EntryText(R.string.entry_how_title)
    HowToUse()
    EntryLink(R.string.entry_help, help)
}

@Composable
private fun HowToUse() {
    listOf(R.string.entry_how_1, R.string.entry_how_2, R.string.entry_how_3).forEachIndexed { index, text ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            Text("${index + 1}.", style = MaterialTheme.typography.titleLarge)
            EntryText(text)
        }
    }
}

@Composable
internal fun SettingsBody(
    state: HelperEntryUiState,
    setup: HelperSetupActions,
    actions: HelperSettingsActions,
) {
    EntryTitle(R.string.entry_settings_title)
    EntrySwitch(R.string.entry_speech_toggle, state.config.helperSpeechEnabled, actions::setSpeech)
    if (!state.config.builtInApiKeyInUse) {
        EntryCard {
            Text(stringResource(R.string.entry_key_title), style = MaterialTheme.typography.titleLarge)
            AiKeyControls(state.setup.aiConnected, setup)
        }
    }
    EntryLink(R.string.entry_reset, actions::showIntroAgain)
}

@StringRes
private fun statusTitle(status: HomeStatus): Int =
    when (status) {
        HomeStatus.MISSING -> R.string.entry_status_missing
        HomeStatus.OFF -> R.string.entry_status_off
        HomeStatus.STARTING -> R.string.entry_status_starting
        HomeStatus.ON -> R.string.entry_status_on
        HomeStatus.FAILED -> R.string.entry_status_failed
    }

@StringRes
private fun statusBody(status: HomeStatus): Int =
    when (status) {
        HomeStatus.MISSING -> R.string.entry_body_missing
        HomeStatus.OFF -> R.string.entry_body_off
        HomeStatus.STARTING -> R.string.entry_body_starting
        HomeStatus.ON -> R.string.entry_body_on
        HomeStatus.FAILED -> R.string.entry_body_failed
    }
