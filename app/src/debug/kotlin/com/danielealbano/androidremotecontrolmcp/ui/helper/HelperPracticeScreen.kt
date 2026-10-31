@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R

/** A local guided simulation. Only the current target can advance the lesson. */
@Composable
internal fun HelperPracticeScreen(
    model: HelperEntryModel,
    onNext: () -> Unit,
    onExit: () -> Unit,
    onBackToHelp: () -> Unit,
) {
    BackHandler(onBack = onExit)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.helper_practice_notice), fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onExit, modifier = Modifier.widthIn(max = 112.dp).heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.helper_practice_exit), fontSize = 18.sp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // This app is scenery, not a second set of controls or accessibility targets.
                Column(Modifier.fillMaxSize().clearAndSetSemantics {}.padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.Settings, null, Modifier.size(28.dp))
                        Text(stringResource(R.string.helper_practice_settings), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    for (label in listOf(
                        R.string.helper_practice_connections,
                        R.string.helper_practice_sound,
                        R.string.helper_practice_notifications,
                        R.string.helper_practice_display,
                        R.string.helper_practice_battery,
                    )) {
                        Text(stringResource(label), fontSize = 22.sp, modifier = Modifier.padding(vertical = 16.dp))
                    }
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)))
                key(model.step) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                            horizontal =
                                if (model.step ==
                                    HelperPracticeStep.ASK
                                ) {
                                    0.dp
                                } else {
                                    20.dp
                                },
                            vertical = 20.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
                    ) {
                        Box(Modifier.padding(horizontal = if (model.step == HelperPracticeStep.ASK) 20.dp else 0.dp)) {
                            PracticeCoach(model.step)
                        }
                        when (model.step) {
                            HelperPracticeStep.OPEN -> {
                                Box(
                                    Modifier
                                        .align(Alignment.End)
                                        .border(2.dp, Color.White, RoundedCornerShape(34.dp))
                                        .padding(6.dp),
                                ) {
                                    HelperEdgeCharacter(onExpand = onNext)
                                }
                            }

                            HelperPracticeStep.ASK -> {
                                HelperPracticeQuestion(onNext, onBackToHelp)
                            }

                            HelperPracticeStep.FOLLOW -> {
                                Surface(
                                    onClick = onNext,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                                ) {
                                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            stringResource(R.string.helper_practice_display),
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(stringResource(R.string.helper_practice_display_detail), fontSize = 18.sp)
                                    }
                                }
                            }

                            HelperPracticeStep.DONE -> {
                                PracticeAction(R.string.helper_practice_complete, onNext)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeCoach(step: HelperPracticeStep) {
    val title =
        when (step) {
            HelperPracticeStep.OPEN -> R.string.helper_tour_open_title
            HelperPracticeStep.ASK -> R.string.helper_tour_ask_title
            HelperPracticeStep.FOLLOW -> R.string.helper_tour_follow_title
            HelperPracticeStep.DONE -> R.string.helper_tour_done_title
        }
    val body =
        when (step) {
            HelperPracticeStep.OPEN -> R.string.helper_tour_open_body
            HelperPracticeStep.ASK -> R.string.helper_tour_ask_body
            HelperPracticeStep.FOLLOW -> R.string.helper_tour_follow_body
            HelperPracticeStep.DONE -> R.string.helper_tour_done_body
        }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.helper_phone_mascot), null, Modifier.size(72.dp))
            Text(stringResource(R.string.helper_tour_progress, step.ordinal + 1), color = Color.White, fontSize = 18.sp)
        }
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(title),
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(body), fontSize = 18.sp, lineHeight = 28.sp)
            }
        }
    }
}

@Composable
private fun PracticeAction(
    label: Int,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), contentPadding = PaddingValues(20.dp)) {
        Text(stringResource(label), fontSize = 20.sp, lineHeight = 28.sp)
    }
}
