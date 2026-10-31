@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danielealbano.androidremotecontrolmcp.R

@Composable
internal fun HelperPracticeQuestion(
    onNext: () -> Unit,
    onBackToHelp: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focus = LocalFocusManager.current
    var draft by rememberSaveable { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var recognizing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val expected = stringResource(R.string.helper_practice_question)
    val voice =
        remember(context) {
            HelperVoiceInput(context, onTranscript = { draft = it }, onState = { active, busy, status ->
                listening = active
                recognizing = busy
                message = status
            })
        }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // A permission result arriving after backgrounding must never start the microphone.
            if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                voice.start(draft)
            } else if (!granted) {
                message = context.getString(R.string.helper_voice_permission_denied)
            }
        }
    DisposableEffect(voice, lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) voice.cancel()
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            voice.cancel()
        }
    }
    val matches = practiceQuestionMatches(draft, expected)
    HelperOverlaySpeech(
        request = draft,
        onRequestChange = {
            voice.cancel()
            draft = it
        },
        onCollapse = {
            voice.cancel()
            onBackToHelp()
        },
        maxHeightDp = 640,
        submitAllowed = matches,
        isListening = listening,
        isRecognizing = recognizing,
        voiceStatus = message ?: stringResource(if (matches) R.string.helper_tour_input_ready else R.string.helper_tour_input_hint),
        onVoiceToggle = {
            if (listening) {
                voice.stop()
            } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voice.start(draft)
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onSubmit = {
            if (matches && !recognizing) {
                voice.cancel()
                focus.clearFocus()
                onNext()
            }
        },
    )
}
