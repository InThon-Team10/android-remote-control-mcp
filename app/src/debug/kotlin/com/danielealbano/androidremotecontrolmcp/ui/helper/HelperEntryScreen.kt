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
    onSettings: () -> Unit,
    onOpenPlayground: () -> Unit,
) {
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
            if (model.page == HelperEntryPage.PRACTICE) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                        Text(stringResource(R.string.helper_onboard_practice_notice), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.helper_onboard_practice_safety), fontSize = 18.sp)
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
                        PracticePhone(model) { onModelChange(model.advance()) }
                    }

                    HelperEntryPage.INVITE -> {
                        Mascot()
                        EntryTitle(stringResource(R.string.helper_onboard_invite_title))
                        EntryBody(stringResource(R.string.helper_onboard_invite_body))
                        EntryAction(stringResource(R.string.helper_onboard_enable_yes), onEnable)
                        EntryLink(stringResource(R.string.helper_onboard_replay)) { onModelChange(model.practice()) }
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
                        Text(
                            if (visible) {
                                stringResource(
                                    R.string.helper_onboard_home_on_body,
                                )
                            } else {
                                stringResource(R.string.helper_onboard_home_off_body)
                            },
                            fontSize = 18.sp,
                            lineHeight = 28.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                        )
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
                        EntryLink(stringResource(R.string.helper_onboard_ai_settings), onSettings)
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
        Text(text, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun EntryLink(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(text, fontSize = 18.sp) }
}

@Composable
private fun PracticePhone(
    model: HelperEntryModel,
    onNext: () -> Unit,
) {
    val step = model.step
    EntryTitle(
        when (step) {
            HelperPracticeStep.OPEN -> stringResource(R.string.helper_onboard_practice_open_title)
            HelperPracticeStep.ASK -> stringResource(R.string.helper_onboard_practice_ask_title)
            HelperPracticeStep.FOLLOW -> stringResource(R.string.helper_onboard_practice_follow_title)
            HelperPracticeStep.DONE -> stringResource(R.string.helper_onboard_practice_done_title)
        },
        small = true,
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("9:41", fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {})
                Box(Modifier.size(width = 64.dp, height = 8.dp).background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)))
            }
            Text(stringResource(R.string.helper_onboard_gallery_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            when (step) {
                HelperPracticeStep.OPEN -> {
                    EntryBody(stringResource(R.string.helper_onboard_gallery_task))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            stringResource(R.string.helper_onboard_album_travel),
                            stringResource(R.string.helper_onboard_album_daily),
                        ).forEach { label ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 96.dp)
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(12.dp),
                                    ).padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                    Button(onClick = onNext, modifier = Modifier.align(Alignment.End).heightIn(min = 64.dp)) {
                        Image(painterResource(R.drawable.helper_phone_mascot), null, Modifier.size(40.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.helper_onboard_help_button), fontSize = 18.sp)
                    }
                }

                HelperPracticeStep.ASK -> {
                    EntryBody(stringResource(R.string.helper_onboard_question_intro))
                    Text(
                        stringResource(R.string.helper_onboard_sample_question),
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    EntryAction(stringResource(R.string.helper_onboard_ask_sample), onNext)
                    EntryBody(stringResource(R.string.helper_onboard_request_is_fake))
                }

                HelperPracticeStep.FOLLOW -> {
                    EntryBody(stringResource(R.string.helper_onboard_target_hint))
                    OutlinedButton(
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    ) { Text(stringResource(R.string.helper_onboard_search_photos), fontSize = 20.sp) }
                    EntryBody(stringResource(R.string.helper_onboard_user_control))
                }

                HelperPracticeStep.DONE -> {
                    Text(
                        stringResource(R.string.helper_onboard_practice_success),
                        fontSize = 22.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    EntryBody(stringResource(R.string.helper_onboard_practice_recap))
                    EntryBody(stringResource(R.string.helper_onboard_result_is_fake))
                    EntryAction(stringResource(R.string.helper_onboard_finish_practice), onNext)
                }
            }
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
                    .size(width = 88.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp)),
            )
        }
    }
}
