@file:Suppress("FunctionNaming", "MagicNumber", "LongMethod", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R

@Composable
fun HelperEntryScreen(
    model: HelperEntryModel,
    onModelChange: (HelperEntryModel) -> Unit,
    visible: Boolean,
    connected: Boolean,
    permissionError: Boolean,
    onEnable: () -> Unit,
    onPermission: () -> Unit,
    onAsk: () -> Unit,
    onHide: () -> Unit,
    onConsent: () -> Unit,
    onMicrophone: () -> Unit,
    onSkipMicrophone: () -> Unit,
    setupBusy: Boolean,
    setupError: Boolean,
    onOpenPlayground: () -> Unit,
) {
    if (model.page == HelperEntryPage.PRACTICE) {
        HelperPracticeScreen(
            model = model,
            onNext = { onModelChange(model.advance()) },
            onBackToHelp = { onModelChange(model.practice()) },
            onExit = { onModelChange(model.copy(page = HelperEntryPage.HOME)) },
        )
        return
    }
    val scrollState = key(model.page, model.step) { rememberScrollState() }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.helper_onboard_brand),
                    textAlign = if (model.page == HelperEntryPage.HOME) TextAlign.Center else TextAlign.Start,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (model.page != HelperEntryPage.HOME) {
                    TextButton(
                        onClick = { onModelChange(model.copy(page = HelperEntryPage.HOME)) },
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        Text(stringResource(R.string.helper_onboard_skip), fontSize = 18.sp)
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (model.page) {
                    HelperEntryPage.WELCOME -> {
                        Mascot()
                        EntryTitle(stringResource(R.string.helper_onboard_welcome_title))
                        EntryBody(stringResource(R.string.helper_onboard_welcome_body))
                        EntryTitle(stringResource(R.string.helper_onboard_welcome_invite), small = true)
                        EntryBody(stringResource(R.string.helper_onboard_welcome_safety))
                        EntryAction(stringResource(R.string.helper_onboard_start_practice)) { onModelChange(model.practice()) }
                    }

                    HelperEntryPage.PRACTICE -> {
                        Unit
                    }

                    HelperEntryPage.INVITE -> {
                        Mascot()
                        EntryTitle(stringResource(R.string.helper_onboard_invite_title))
                        EntryBody(stringResource(R.string.helper_onboard_invite_body))
                        EntryAction(stringResource(R.string.helper_onboard_enable_yes), onEnable)
                        EntryLink(stringResource(R.string.helper_onboard_replay)) { onModelChange(model.practice()) }
                    }

                    HelperEntryPage.CONSENT -> {
                        EntryTitle(stringResource(R.string.helper_consent_title))
                        EntryBody(stringResource(R.string.helper_consent_body))
                        EntryBody(stringResource(R.string.helper_consent_control))
                        if (setupError) EntryBody(stringResource(R.string.helper_consent_error))
                        Button(onClick = onConsent, enabled = !setupBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(stringResource(R.string.helper_consent_accept), fontSize = 18.sp)
                        }
                        EntryLink(stringResource(R.string.helper_consent_defer)) { onModelChange(model.copy(page = HelperEntryPage.HOME)) }
                    }

                    HelperEntryPage.MICROPHONE -> {
                        EntryTitle(stringResource(R.string.helper_mic_setup_title))
                        EntryBody(stringResource(R.string.helper_mic_setup_body))
                        EntryBody(stringResource(R.string.helper_voice_privacy))
                        EntryAction(stringResource(R.string.helper_mic_setup_allow), onMicrophone)
                        EntryLink(stringResource(R.string.helper_mic_setup_skip), onSkipMicrophone)
                    }

                    HelperEntryPage.PERMISSION -> {
                        EntryTitle(stringResource(R.string.helper_onboard_permission_title))
                        EntryBody(stringResource(R.string.helper_onboard_permission_steps))
                        EntryBody(stringResource(R.string.helper_onboard_permission_choice))
                        if (connected) {
                            EntryBody(stringResource(R.string.helper_onboard_permission_ready))
                            EntryAction(stringResource(R.string.helper_onboard_enable), onEnable)
                        } else {
                            if (permissionError) EntryBody(stringResource(R.string.helper_onboard_permission_error))
                            EntryAction(stringResource(R.string.helper_onboard_open_settings), onPermission)
                        }
                        EntryLink(stringResource(R.string.helper_onboard_defer)) { onModelChange(model.copy(page = HelperEntryPage.HOME)) }
                    }

                    HelperEntryPage.HOME -> {
                        HelperHomeStatus(visible = visible)
                        EntryAction(
                            if (visible) {
                                stringResource(
                                    R.string.helper_onboard_ask,
                                )
                            } else {
                                stringResource(R.string.helper_onboard_enable_home)
                            },
                            if (visible) onAsk else onEnable,
                        )
                        if (visible) EntryLink(stringResource(R.string.helper_onboard_pause), onHide)
                        HorizontalDivider()
                        EntryLink(stringResource(R.string.helper_onboard_practice_again)) { onModelChange(model.practice()) }

                        EntryLink(stringResource(R.string.helper_onboard_developer_tools), onOpenPlayground)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Mascot() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.helper_phone_mascot), contentDescription = null, modifier = Modifier.size(156.dp))
    }
}

@Composable
private fun EntryTitle(
    text: String,
    small: Boolean = false,
) {
    Text(
        text,
        fontSize = if (small) 24.sp else 32.sp,
        lineHeight = if (small) 34.sp else 42.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun EntryBody(text: String) {
    Text(text, fontSize = 18.sp, lineHeight = 28.sp)
}

@Composable
private fun EntryAction(
    text: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), contentPadding = PaddingValues(16.dp)) {
        Text(
            text,
            fontSize = 18.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading),
        )
    }
}

@Composable
private fun EntryLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(text, fontSize = 18.sp) }
}
