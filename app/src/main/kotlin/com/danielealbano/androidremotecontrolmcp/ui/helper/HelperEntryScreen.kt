@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.helper.practice.HelperPracticeScreen

private val CONTENT_MAX_WIDTH = 560.dp
private val SCREEN_PADDING = 24.dp
private val CONTENT_SPACING = 20.dp
private val END_SPACER = 12.dp
private val APP_TITLE_SIZE = 22.sp

@Composable
fun HelperEntryScreen(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
    setup: HelperSetupActions,
) {
    Surface(Modifier.fillMaxSize()) {
        if (!state.loaded) return@Surface
        if (state.model.page == HelperEntryPage.PRACTICE) {
            HelperPracticeScreen(state, actions)
            return@Surface
        }
        Column(Modifier.safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            EntryHeader(state.model, actions)
            key(state.model.page, state.model.step) {
                Column(
                    Modifier
                        .weight(1f)
                        .widthIn(max = CONTENT_MAX_WIDTH)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(SCREEN_PADDING),
                    verticalArrangement = Arrangement.spacedBy(CONTENT_SPACING),
                ) {
                    EntryContent(state, actions, setup)
                    Spacer(Modifier.height(END_SPACER))
                }
            }
        }
    }
}

@Composable
private fun EntryHeader(
    model: HelperEntryModel,
    actions: HelperEntryActions,
) {
    val home = model.page == HelperEntryPage.HOME
    Row(
        Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(horizontal = SCREEN_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.page == HelperEntryPage.SETTINGS) {
            EntryLink(R.string.entry_back, { actions.navigate(HelperEntryModel::back) }, Modifier)
        }
        Text(
            stringResource(R.string.app_name),
            Modifier.weight(1f).padding(vertical = 8.dp).semantics { heading() },
            textAlign = if (home) TextAlign.Center else TextAlign.Start,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = APP_TITLE_SIZE, fontWeight = FontWeight.Bold),
        )
        if (home) EntryLink(R.string.entry_settings, { actions.navigate(HelperEntryModel::settings) }, Modifier)
        if (model.page.canBeSkipped()) {
            EntryLink(R.string.helper_onboard_skip, { actions.navigate(HelperEntryModel::skip) }, Modifier)
        }
    }
}

private fun HelperEntryPage.canBeSkipped(): Boolean =
    when (this) {
        HelperEntryPage.WELCOME,
        HelperEntryPage.INVITE,
        HelperEntryPage.CONSENT,
        HelperEntryPage.PERMISSION,
        HelperEntryPage.MICROPHONE,
        -> true

        else -> false
    }

@Composable
private fun EntryContent(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
    setup: HelperSetupActions,
) {
    when (state.model.page) {
        HelperEntryPage.WELCOME -> WelcomeBody(actions)
        HelperEntryPage.INVITE -> InviteBody(actions)
        HelperEntryPage.CONSENT -> ConsentBody(state, actions)
        HelperEntryPage.PERMISSION -> PermissionBody(state, setup, actions)
        HelperEntryPage.MICROPHONE -> MicrophoneBody(setup, actions)
        HelperEntryPage.HOME -> HomeBody(state, actions)
        HelperEntryPage.SETTINGS -> SettingsBody(state, setup, actions)
        HelperEntryPage.PRACTICE -> Unit
    }
}
