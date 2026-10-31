@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme

/** Owned, inert target screen. It performs no payment, deletion, transmission or network call. */
class ConsequencePreviewActivity : ComponentActivity() {
    private var foreground by mutableStateOf(false)

    companion object {
        const val EXTRA_START_TEST = "start_consequence_test"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startTest = savedInstanceState == null && intent.getBooleanExtra(EXTRA_START_TEST, false)
        setContent {
            AndroidRemoteControlMcpTheme(dynamicColor = false) { Playground(startTest, foreground) }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
    }

    override fun onPause() {
        foreground = false
        ConsequencePreviewBridge.onVisibility?.invoke(false)
        super.onPause()
    }

    override fun onDestroy() {
        ConsequencePreviewBridge.onSimulation = null
        ConsequencePreviewBridge.onPicker = null
        super.onDestroy()
    }
}

@Composable
private fun Playground(
    startTest: Boolean,
    foreground: Boolean,
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(startTest) }
    var pendingStart by remember { mutableStateOf(startTest) }
    val variant = helperGuideVariant
    var example by remember { mutableIntStateOf(0) }
    var corner by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(Rect.Zero) }
    var taps by remember { mutableIntStateOf(0) }
    var otherTaps by remember { mutableIntStateOf(0) }

    fun leave() {
        ConsequencePreviewBridge.active = false
        ConsequencePreviewBridge.onLeave?.invoke()
        pendingStart = false
        selected = false
    }
    SideEffect {
        ConsequencePreviewBridge.onSimulation = {
            // Invalidate synchronously BEFORE changing the owned screen.
            version++
            ConsequencePreviewBridge.onUpdate?.invoke(consequenceExample(example, target, version))
            corner = (corner + 1) % 4
        }
        ConsequencePreviewBridge.onPicker = { leave() }
    }
    LaunchedEffect(selected, pendingStart, target, ConsequencePreviewBridge.ready, foreground) {
        if (selected && !pendingStart && target != Rect.Zero && foreground && ConsequencePreviewBridge.active) {
            ConsequencePreviewBridge.onUpdate?.invoke(consequenceExample(example, target, version))
            ConsequencePreviewBridge.onVisibility?.invoke(true)
        }
        if (selected && pendingStart && target != Rect.Zero && ConsequencePreviewBridge.ready && foreground) {
            val start = ConsequencePreviewBridge.onStart ?: return@LaunchedEffect
            pendingStart = false
            ConsequencePreviewBridge.active = true
            start(consequenceExample(example, target, version), variant)
        }
    }
    BackHandler(selected) { leave() }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(16.dp).then(
                if (ConsequencePreviewBridge.touchPolicy == 1) Modifier.clearAndSetSemantics { } else Modifier,
            ),
        ) {
            Text(if (selected) "테스트 화면 · 개발용" else "결과 설명 · 개발용", style = MaterialTheme.typography.titleLarge)
            Text("실제 결제·전송·삭제가 없는 테스트예요.", style = MaterialTheme.typography.bodyLarge)
            if (!selected) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("설명 방식", style = MaterialTheme.typography.titleMedium)
                    Text(if (variant == GuideVariant.A) "A · 항상 설명 먼저" else "B · 말풍선 우선")
                    listOf("짧은 결제", "긴 전송 설명", "삭제", "결과 미확인", "일반 검색").forEachIndexed { i, label ->
                        FilterChip(selected = example == i, onClick = { example = i }, label = { Text(label) })
                    }
                    Text("버튼 위치")
                    Row {
                        listOf("위", "아래", "왼쪽", "오른쪽").forEachIndexed { i, label ->
                            TextButton(onClick = { corner = i }, modifier = Modifier.heightIn(min = 56.dp)) {
                                Text(if (corner == i) "✓ $label" else label)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            target = Rect.Zero
                            pendingStart = true
                            selected = true
                            version = 0
                            taps = 0
                            otherTaps = 0
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) { Text("테스트 화면 열기") }
                }
            } else {
                Text("테스트 버튼 탭 $taps 회 · 다른 버튼 탭 $otherTaps 회")
                Text("탭 횟수만 기록해요. 거래 성공을 뜻하지 않아요.", style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val alignment =
                        when (corner) {
                            1 -> Alignment.BottomCenter
                            2 -> Alignment.CenterStart
                            3 -> Alignment.CenterEnd
                            else -> Alignment.TopCenter
                        }
                    Button(
                        onClick = {
                            if (ConsequencePreviewBridge.touchPolicy == 1) return@Button
                            taps++
                            ConsequencePreviewBridge.active = false
                            ConsequencePreviewBridge.onLeave?.invoke()
                        },
                        modifier =
                            Modifier
                                .align(alignment)
                                .heightIn(min = 56.dp)
                                .onGloballyPositioned {
                                    val next = it.boundsInWindow()
                                    if (next != target) {
                                        target = next
                                        if (ConsequencePreviewBridge.active) {
                                            ConsequencePreviewBridge.onUpdate?.invoke(consequenceExample(example, target, version))
                                        }
                                    }
                                },
                    ) { Text(listOf("결제", "보내기", "삭제", "계속", "검색")[example]) }
                    OutlinedButton(
                        onClick = { if (ConsequencePreviewBridge.touchPolicy == 0) otherTaps++ },
                        modifier = Modifier.align(if (corner == 3) Alignment.CenterStart else Alignment.CenterEnd).heightIn(min = 56.dp),
                    ) { Text("다른 버튼") }
                }
                if (!ConsequencePreviewBridge.ready) {
                    Text("오버레이 미리보기 서비스를 켜 주세요.")
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text("접근성 설정 열기")
                    }
                }
                TextButton(onClick = { leave() }, modifier = Modifier.heightIn(min = 56.dp)) { Text("다른 예시 선택") }
            }
        }
    }
}
