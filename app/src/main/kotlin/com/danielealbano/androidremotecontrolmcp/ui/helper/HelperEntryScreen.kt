@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R

@Composable
fun HelperEntryScreen(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
    setup: HelperSetupActions,
) {
    Surface(Modifier.fillMaxSize()) {
        if (!state.loaded) return@Surface
        Column(Modifier.safeDrawingPadding()) {
            EntryHeader(state.model, actions)
            key(state.model.page) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EntryContent(state, actions, setup)
                }
            }
            EntryFooter(state, actions)
        }
    }
}

@Composable
private fun EntryHeader(
    model: HelperEntryModel,
    actions: HelperEntryActions,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!model.exitsOnBack) {
            EntryLink(R.string.entry_back, navigation(actions, HelperEntryModel::back))
        }
        Text(
            stringResource(R.string.entry_name),
            Modifier.weight(1f).padding(8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        if (model.page == HelperEntryPage.HOME) {
            EntryLink(R.string.entry_settings, navigation(actions, HelperEntryModel::settings))
        }
    }
}

@Composable
private fun EntryContent(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
    setup: HelperSetupActions,
) {
    when (state.model.page) {
        HelperEntryPage.WELCOME -> WelcomeBody { actions.navigate(HelperEntryModel::help) }
        HelperEntryPage.SETUP -> SetupBody(state, setup)
        HelperEntryPage.HOME -> HomeBody(state, actions::turnOff) { actions.navigate(HelperEntryModel::help) }
        HelperEntryPage.HELP -> HelpBody()
        HelperEntryPage.SETTINGS -> SettingsBody(state, setup, actions)
    }
}

@Composable
private fun EntryFooter(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
) {
    val action: () -> Unit =
        when (state.model.page) {
            HelperEntryPage.WELCOME -> actions::startSetup
            HelperEntryPage.SETUP -> setupClick(state.setup.ready, actions)
            HelperEntryPage.HOME -> homeClick(state, actions)
            HelperEntryPage.HELP, HelperEntryPage.SETTINGS -> navigation(actions, HelperEntryModel::back)
        }
    Button(
        onClick = action,
        enabled = !(state.busy || state.bubble == HelperBubbleStatus.STARTING),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 56.dp),
    ) {
        EntryText(primaryLabel(state))
    }
}

private fun navigation(
    actions: HelperEntryActions,
    transition: (HelperEntryModel) -> HelperEntryModel,
): () -> Unit = { actions.navigate(transition) }

private fun setupClick(
    ready: Boolean,
    actions: HelperEntryActions,
): () -> Unit = if (ready) actions::turnOn else navigation(actions, HelperEntryModel::home)

private fun homeClick(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
): () -> Unit =
    when (state.homeAction) {
        HomeAction.CONTINUE_SETUP -> navigation(actions, HelperEntryModel::setup)
        HomeAction.START, HomeAction.RETRY -> actions::turnOn
        HomeAction.OPEN -> actions::openHelper
        HomeAction.STARTING -> ({})
    }

@StringRes
private fun primaryLabel(state: HelperEntryUiState): Int =
    when (state.model.page) {
        HelperEntryPage.WELCOME -> R.string.entry_setup_start
        HelperEntryPage.HELP -> R.string.entry_return
        HelperEntryPage.SETTINGS -> R.string.entry_home
        HelperEntryPage.SETUP -> if (state.setup.ready) R.string.entry_start else R.string.entry_later
        HelperEntryPage.HOME -> homeLabel(state.homeAction)
    }

@StringRes
private fun homeLabel(action: HomeAction): Int =
    when (action) {
        HomeAction.CONTINUE_SETUP -> R.string.entry_continue_setup
        HomeAction.START -> R.string.entry_start
        HomeAction.STARTING -> R.string.entry_starting
        HomeAction.OPEN -> R.string.entry_open
        HomeAction.RETRY -> R.string.entry_retry
    }
