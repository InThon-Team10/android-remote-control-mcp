@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperEntryActions
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperEntryModel
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperEntryUiState
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperPracticeStep
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.HelperRequestInput
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.MicVisual
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.RequestInputActions
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.RequestInputUi
import com.danielealbano.androidremotecontrolmcp.ui.helper.practiceMicEnabled
import com.danielealbano.androidremotecontrolmcp.ui.helper.practiceQuestionMatches

private val CONTENT_MAX_WIDTH = 560.dp
private val COACH_PADDING = 20.dp
private val SHEET_CORNER = 28.dp
private val SHEET_PADDING = 16.dp
private val PRIVACY_SIZE = 18.sp
private val PRIVACY_LINE = 26.sp

/** What the practice question input needs from its dictation and the microphone permission, as one unit. */
private class QuestionVoice(
    val dictation: PracticeDictation,
    val request: () -> Unit,
)

/**
 * The practice's ASK step: the shared request input, filled by typing or by on-device dictation. The question is
 * never sent anywhere; "요청 보내기" only moves the practice on. Dictation never overlaps a live conversation.
 */
@Composable
internal fun PracticeQuestion(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var micUsed by rememberSaveable { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val voice = rememberQuestionVoice(state.conversationActive, { draft }, { draft = it }) { denied = true }
    val dictation by voice.dictation.state.collectAsState()
    val expected = stringResource(R.string.helper_practice_question)
    val matches = practiceQuestionMatches(draft, expected)
    val micVisual = micVisualFor(voice.dictation.available, state.conversationActive, dictation)
    val status = questionStatus(dictation, denied, matches)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        PracticeCoach(
            HelperPracticeStep.ASK,
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(COACH_PADDING),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
            modifier = Modifier.fillMaxWidth().widthIn(max = CONTENT_MAX_WIDTH).align(Alignment.CenterHorizontally),
        ) {
            HelperRequestInput(
                ui = RequestInputUi(draft, false, micVisual, status, matches && !dictation.busy),
                actions =
                    RequestInputActions(
                        onTextChange = {
                            voice.dictation.cancel()
                            draft = it
                        },
                        onMic = {
                            micUsed = true
                            denied = false
                            if (dictation.listening) voice.dictation.stop() else voice.request()
                        },
                        onSend = {
                            focus.clearFocus()
                            actions.navigate(HelperEntryModel::advance)
                        },
                        onClose = {
                            voice.dictation.cancel()
                            actions.navigate(HelperEntryModel::practice)
                        },
                    ),
                modifier = Modifier.padding(SHEET_PADDING),
                below = {
                    if (micUsed) {
                        Text(
                            stringResource(R.string.helper_voice_privacy),
                            fontSize = PRIVACY_SIZE,
                            lineHeight = PRIVACY_LINE,
                        )
                    }
                },
            )
        }
    }
}

private fun micVisualFor(
    available: Boolean,
    conversationActive: Boolean,
    dictation: PracticeDictationState,
): MicVisual =
    when {
        !available || !practiceMicEnabled(conversationActive) -> MicVisual.HIDDEN
        dictation.listening -> MicVisual.LISTENING
        dictation.recognizing -> MicVisual.PROCESSING
        else -> MicVisual.IDLE
    }

@Composable
private fun questionStatus(
    dictation: PracticeDictationState,
    denied: Boolean,
    matches: Boolean,
): String =
    when {
        dictation.message != null -> dictation.message
        denied -> stringResource(R.string.helper_voice_permission_denied)
        matches -> stringResource(R.string.helper_tour_input_ready)
        else -> stringResource(R.string.helper_tour_input_hint)
    }

/**
 * Creates the dictation and the permission launcher. Dictation is cancelled when the screen stops or leaves, and
 * whenever a live conversation becomes active; a granted permission starts it only while the screen is resumed.
 */
@Composable
private fun rememberQuestionVoice(
    conversationActive: Boolean,
    draft: () -> String,
    onTranscript: (String) -> Unit,
    onDenied: () -> Unit,
): QuestionVoice {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentActive by rememberUpdatedState(conversationActive)
    val currentDraft by rememberUpdatedState(draft)
    val currentTranscript by rememberUpdatedState(onTranscript)
    val dictation =
        remember(context) {
            OnDevicePracticeDictation(context, { currentTranscript(it) }, { currentActive })
        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                onDenied()
            } else if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                dictation.start(currentDraft())
            }
        }
    LaunchedEffect(conversationActive) { if (conversationActive) dictation.cancel() }
    DisposableEffect(lifecycle, dictation) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) dictation.cancel() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            dictation.cancel()
        }
    }
    return remember(dictation, launcher) {
        QuestionVoice(dictation) {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (granted) dictation.start(currentDraft()) else launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}
