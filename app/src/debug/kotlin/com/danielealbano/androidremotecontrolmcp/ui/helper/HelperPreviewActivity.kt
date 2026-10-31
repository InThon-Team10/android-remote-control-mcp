@file:Suppress("FunctionNaming", "LongMethod", "UnusedPrivateFunction")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme

/** Debug-only launcher. No microphone, network or accessibility actions are performed. */
class HelperPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidRemoteControlMcpTheme(dynamicColor = false) {
                var playground by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = playground) { playground = false }
                if (playground) {
                    HelperPlayground()
                } else {
                    HelperEntryPreview(onOpenPlayground = { playground = true })
                }
            }
        }
    }
}

@Composable
private fun HelperPlayground() {
    val context = LocalContext.current
    var request by rememberSaveable { mutableStateOf("") }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val microphoneNotice = stringResource(R.string.helper_preview_microphone)
    BackHandler(enabled = showGuide) { showGuide = false }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            Text(
                text = stringResource(R.string.helper_preview_notice),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(16.dp),
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.helper_overlay_launch))
            }
            Button(
                onClick = { context.startActivity(Intent(context, ConsequencePreviewActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("결과 설명 테스트") }
            if (showGuide) {
                GuidePlayground(onClose = { showGuide = false })
            } else {
                HelperRequestScreen(
                    request = request,
                    onRequestChange = { request = it },
                    onSubmit = { showGuide = true },
                    onMicrophoneClick = { message = microphoneNotice },
                    message = message,
                )
            }
        }
    }
}

@Composable
private fun GuidePlayground(onClose: () -> Unit) {
    var target by remember { mutableStateOf<Rect?>(null) }
    var clicked by rememberSaveable { mutableStateOf(false) }
    var repeatNotice by rememberSaveable { mutableStateOf(false) }
    var showHighlight by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Button(
                onClick = { clicked = true },
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .onGloballyPositioned { target = it.boundsInParent() },
            ) {
                Text(stringResource(R.string.helper_preview_target))
            }
            if (showHighlight) {
                SpotlightOverlay(target = target, modifier = Modifier.matchParentSize())
            }
        }
        GuidanceCard(
            instruction =
                stringResource(
                    if (clicked) R.string.helper_preview_success else R.string.helper_preview_instruction,
                ),
            onRepeat = { repeatNotice = true },
            onClose = onClose,
        )
        if (repeatNotice) {
            Text(stringResource(R.string.helper_preview_voice))
        }
        HelperBubble(onClick = { showHighlight = !showHighlight })
        Text(stringResource(R.string.helper_preview_bubble_hint))
    }
}

@Preview(name = "Request", showBackground = true, locale = "ko", widthDp = 360, heightDp = 800)
@Preview(name = "Large text", showBackground = true, locale = "ko", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun RequestPreview() {
    AndroidRemoteControlMcpTheme(dynamicColor = false) {
        HelperPlayground()
    }
}

@Preview(name = "Guidance", showBackground = true, locale = "ko", widthDp = 360)
@Composable
private fun GuidancePreview() {
    AndroidRemoteControlMcpTheme(dynamicColor = false) {
        Surface { GuidePlayground(onClose = {}) }
    }
}

@Preview(name = "Dark / preparing", showBackground = true, locale = "ko", widthDp = 360)
@Composable
private fun PreparingPreview() {
    AndroidRemoteControlMcpTheme(darkTheme = true, dynamicColor = false) {
        Surface {
            HelperRequestScreen(
                request = stringResource(R.string.helper_request_example),
                onRequestChange = {},
                onSubmit = {},
                onMicrophoneClick = {},
                busy = true,
            )
        }
    }
}
