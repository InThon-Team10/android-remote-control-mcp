@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.runtime.Composable
import com.danielealbano.androidremotecontrolmcp.R

@Composable
internal fun WelcomeBody(actions: HelperEntryActions) {
    EntryMascot()
    EntryTitle(R.string.helper_onboard_welcome_title)
    EntryText(R.string.helper_onboard_welcome_body)
    EntryTitle(R.string.helper_onboard_welcome_invite, compact = true)
    EntryText(R.string.helper_onboard_welcome_safety)
    EntryAction(R.string.helper_onboard_start_practice, actions::startPractice)
}

@Composable
internal fun InviteBody(actions: HelperEntryActions) {
    EntryMascot()
    EntryTitle(R.string.helper_onboard_invite_title)
    EntryText(R.string.helper_onboard_invite_body)
    EntryAction(R.string.helper_onboard_enable_yes, actions::continueSetup)
    EntryLink(R.string.helper_onboard_replay, actions::startPractice)
}
