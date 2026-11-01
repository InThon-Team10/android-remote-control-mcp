@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.os.Build
import androidx.compose.runtime.Composable
import com.danielealbano.androidremotecontrolmcp.R

@Composable
internal fun ConsentBody(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
) {
    EntryTitle(R.string.helper_consent_title)
    EntryText(R.string.helper_consent_body)
    EntryText(R.string.helper_consent_control)
    state.errorRes?.let { EntryText(it) }
    EntryAction(R.string.helper_consent_accept, actions::acceptConsent, enabled = !state.busy)
    EntryLink(R.string.helper_consent_defer, { actions.navigate(HelperEntryModel::home) })
}

@Composable
internal fun PermissionBody(
    state: HelperEntryUiState,
    setup: HelperSetupActions,
    actions: HelperEntryActions,
) {
    EntryTitle(R.string.helper_onboard_permission_title)
    if (state.accessibilityReady) {
        EntryText(R.string.helper_onboard_permission_ready)
        EntryAction(R.string.helper_onboard_enable_home, actions::continueSetup, enabled = !state.busy)
    } else {
        EntryText(R.string.helper_onboard_permission_steps)
        EntryText(R.string.helper_onboard_permission_choice)
        state.errorRes?.let { EntryText(it) }
        EntryAction(R.string.helper_onboard_open_settings, setup::openAccessibilitySettings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EntryText(R.string.entry_restricted_hint)
            EntryAction(R.string.entry_open_app_info, setup::openAppInfo)
        }
    }
    EntryLink(R.string.helper_onboard_defer, { actions.navigate(HelperEntryModel::home) })
}

@Composable
internal fun MicrophoneBody(
    setup: HelperSetupActions,
    actions: HelperEntryActions,
) {
    EntryTitle(R.string.helper_mic_setup_title)
    EntryText(R.string.helper_mic_setup_body)
    EntryText(R.string.helper_mic_setup_privacy)
    EntryAction(R.string.helper_mic_setup_allow, setup::requestMicrophone)
    EntryLink(R.string.helper_mic_setup_skip, actions::skipMicrophone)
}
