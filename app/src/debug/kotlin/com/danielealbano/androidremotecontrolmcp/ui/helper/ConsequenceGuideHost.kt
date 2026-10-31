@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.theme.LightColorScheme

/** Only handles the owned, inert test Activity through the in-process bridge. */
internal class ConsequenceGuideHost(
    private val service: HelperOverlayPreviewService,
    private val clear: () -> Unit,
    private val attach: (WindowManager.LayoutParams, @Composable () -> Unit) -> Unit,
    private val bubble: () -> Unit,
    private val speak: (String) -> Boolean,
    private val stopSpeech: () -> Unit,
    private val speechReady: () -> Boolean,
) {
    val state = ConsequenceState()
    private val manager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var menu = false
    private var foreground = true
    var live = false
        private set
    private var liveRefresh: (() -> Unit)? = null
    private var liveValidate: (((() -> Unit)) -> Unit)? = null

    fun beginLive(
        step: ConsequenceStep,
        variant: GuideVariant,
        refresh: () -> Unit,
        validate: ((() -> Unit) -> Unit),
    ) {
        finish()
        live = true
        foreground = true
        liveRefresh = refresh
        liveValidate = validate
        ConsequencePreviewBridge.active = true
        state.begin(step, variant)
        render()
    }

    fun invalidateLive() {
        if (!live || !foreground) return
        foreground = false
        stopSpeech()
        state.step?.let { state.replace(it.copy(version = it.version + 1)) }
        render()
    }

    private var readFailed = false
    private val preferences = service.getSharedPreferences("helper_voice", android.content.Context.MODE_PRIVATE)
    private var autoRead = preferences.getBoolean("auto_read", true)
    private val narration = ConsequenceNarration()

    private fun autoReadOnce(step: ConsequenceStep) {
        val screenReader = service.getSystemService(AccessibilityManager::class.java).isTouchExplorationEnabled
        if (!foreground) return
        if (!narration.claim(step, autoRead, speechReady(), screenReader, menu)) return
        readFailed = !speak(step.fullText)
        if (readFailed) render()
    }

    fun connect() {
        ConsequencePreviewBridge.ready = true
        ConsequencePreviewBridge.onStart = { step, variant ->
            finish()
            stopSpeech()
            narration.spokenStep = null
            foreground = true
            ConsequencePreviewBridge.active = true
            state.begin(step, variant)
            menu = false
            readFailed = false
            render()
        }
        ConsequencePreviewBridge.onUpdate = { step ->
            if (ConsequencePreviewBridge.active) {
                stopSpeech()
                state.replace(step)
                menu = false
                render()
            }
        }
        ConsequencePreviewBridge.onLeave = { close() }
        ConsequencePreviewBridge.onVisibility = { visible ->
            if (!live && foreground != visible) {
                foreground = visible
                if (!visible) stopSpeech()
                if (ConsequencePreviewBridge.active) {
                    state.step?.let { state.replace(it.copy(version = it.version + 1)) }
                    render()
                }
            }
        }
    }

    fun finish() {
        generation++
        live = false
        liveRefresh = null
        liveValidate = null
        handler.removeCallbacksAndMessages(null)
        ConsequencePreviewBridge.active = false
        ConsequencePreviewBridge.touchPolicy = 0
        stopSpeech()
        state.finish()
        narration.spokenStep = null
        menu = false
        readFailed = false
    }

    fun disconnect() {
        finish()
        ConsequencePreviewBridge.touchPolicy = 0
        ConsequencePreviewBridge.ready = false
        ConsequencePreviewBridge.active = false
        ConsequencePreviewBridge.onStart = null
        ConsequencePreviewBridge.onUpdate = null
        ConsequencePreviewBridge.onLeave = null
        ConsequencePreviewBridge.onVisibility = null
        handler.removeCallbacksAndMessages(null)
    }

    fun refresh() {
        if (live) {
            invalidateLive()
            return
        }
        state.step?.let { state.replace(it.copy(version = it.version + 1)) }
        render()
    }

    private fun close() {
        finish()
        clear()
        bubble()
    }

    private fun safeInsets() =
        manager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()

    private fun params(
        width: Int,
        height: Int,
        x: Int = 0,
        y: Int = 0,
        touch: Boolean = false,
    ) = WindowManager
        .LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT,
        ).apply {
            setFitInsetsTypes(0)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x + safeInsets().left
            this.y = y + safeInsets().top
        }

    private fun read(text: String) {
        narration.spokenStep = state.step
        readFailed = !speak(text)
        render()
    }

    private fun block(rect: Rect) {
        if (rect.width <= 0 || rect.height <= 0) return
        attach(params(rect.width.toInt(), rect.height.toInt(), rect.left.toInt(), rect.top.toInt(), true)) {
            Box(
                Modifier.fillMaxSize().clearAndSetSemantics { }.pointerInput(Unit) {
                    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                },
            )
        }
    }

    fun render() {
        if (!ConsequencePreviewBridge.active) return
        val step = state.step ?: return
        val token = ++generation
        clear()
        val metrics = manager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val width = metrics.bounds.width() - insets.left - insets.right
        val height = metrics.bounds.height() - insets.top - insets.bottom
        val target = step.target.translate(-insets.left.toFloat(), -insets.top.toFloat())
        val valid =
            target.width > 0 && target.height > 0 && target.left >= 0 && target.top >= 0 &&
                target.right <= width && target.bottom <= height
        val below = height - target.bottom.toInt() - dp(32)
        val above = target.top.toInt() - dp(32)
        val captionBelow = below >= above
        val available = if (captionBelow) below else above
        val fits = valid && foreground && available >= dp(240)
        state.fit(fits)
        val open = state.targetOpen && !menu
        ConsequencePreviewBridge.touchPolicy =
            if (!foreground || !step.important || (live && !valid)) {
                0
            } else if (open) {
                2
            } else {
                1
            }
        if (foreground && step.important && (!live || valid)) {
            if (!open) {
                block(Rect(0f, 0f, width.toFloat(), height.toFloat()))
            } else {
                // Four separate windows leave a real touch-through hole, not a synthetic click.
                block(Rect(0f, 0f, width.toFloat(), target.top))
                block(Rect(0f, target.bottom, width.toFloat(), height.toFloat()))
                block(Rect(0f, target.top, target.left, target.bottom))
                block(Rect(target.right, target.top, width.toFloat(), target.bottom))
            }
        }
        attach(params(width, height)) {
            val ready = speechReady()
            LaunchedEffect(step, ready, autoRead) {
                if (token == generation && ConsequencePreviewBridge.active) autoReadOnce(step)
            }
            HelperOverlayFrame(
                guiding = false,
                targetBounds = target,
                captionVisible = false,
                highlightVisible = open,
            )
        }
        if (state.phase != ConsequencePhase.TARGET || menu) {
            val panel = params(width, WindowManager.LayoutParams.WRAP_CONTENT, touch = true)
            panel.gravity = Gravity.BOTTOM or Gravity.LEFT
            panel.y = dp(8) + insets.bottom
            attach(panel) {
                ConsequencePanel(
                    step = step,
                    phase = state.phase,
                    foreground = foreground,
                    live = live,
                    onRefresh = { liveRefresh?.invoke() },
                    menu = menu,
                    maxHeight = (height / service.resources.displayMetrics.density - 16).toInt(),
                    canLocate = state.canLocate,
                    speechReady = speechReady() && !readFailed,
                    autoRead = autoRead,
                    onAutoReadChange = { enabled ->
                        autoRead = enabled
                        preferences.edit().putBoolean("auto_read", enabled).apply()
                        if (!enabled) stopSpeech() else narration.spokenStep = null
                        render()
                    },
                    onReturn = { close() },
                    onToggleMenu = {
                        menu = !menu
                        render()
                    },
                    onLocate = {
                        val locate = {
                            if (token == generation && ConsequencePreviewBridge.active && foreground) {
                                state.locate()
                                render()
                            }
                        }
                        if (live) liveValidate?.invoke(locate) else locate()
                    },
                    onRead = { read(step.fullText) },
                    onExplain = {
                        menu = false
                        state.compact = true
                        state.measured = false
                        state.phase = if (state.variant == GuideVariant.A) ConsequencePhase.EXPLANATION else ConsequencePhase.TARGET
                        render()
                    },
                    onRetry = {
                        close()
                        ConsequencePreviewBridge.onPicker?.invoke()
                    },
                    onChange = { if (live) liveRefresh?.invoke() else ConsequencePreviewBridge.onSimulation?.invoke() },
                )
            }
        } else {
            // A touchable window owns both caption and reading action; the dim layer never catches taps.
            val captionHeight = if (fits) available else height - dp(32)
            val captionTop = if (fits && captionBelow) target.bottom.toInt() + dp(16) else dp(16)
            val captionWidth = minOf(width - dp(32), dp(420))
            val captionParams = params(captionWidth, WindowManager.LayoutParams.WRAP_CONTENT, (width - captionWidth) / 2, captionTop, true)
            if (fits && !captionBelow) {
                captionParams.gravity = Gravity.BOTTOM or Gravity.LEFT
                captionParams.y = insets.bottom + height - target.top.toInt() + dp(16)
            }
            attach(captionParams) {
                Column(
                    Modifier.heightIn(max = (captionHeight / service.resources.displayMetrics.density).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val controls: @Composable () -> Unit = {
                        HelperControlActions(
                            onReturn = { close() },
                            secondaryLabel = R.string.helper_overlay_menu,
                            onSecondary = {
                                menu = true
                                render()
                            },
                        )
                    }
                    if (!captionBelow) controls()
                    Column(Modifier.weight(1f, fill = false)) {
                        if (fits && captionBelow) CaptionPointer(target.center.x - (width - captionWidth) / 2f, true)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = LightColorScheme.surface,
                            contentColor = LightColorScheme.onSurface,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                                    if (!foreground) {
                                        Text(if (live) "화면이 바뀌었어요. 다음 안내를 요청해 주세요." else "테스트 화면을 벗어났어요. 돌아오면 버튼 위치를 다시 확인해요.")
                                    } else if (!fits) {
                                        Text(
                                            if (live &&
                                                !valid
                                            ) {
                                                "강조할 버튼이 없어요. 설명을 확인하고 다음 안내를 요청해 주세요."
                                            } else {
                                                "버튼과 함께 표시할 공간이 부족해요. 화면 방향을 바꿔 주세요."
                                            },
                                        )
                                    }
                                    Text(
                                        step.result,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                    )
                                    if (state.variant == GuideVariant.B || !step.confirmed) {
                                        step.facts.forEach { Text(it, fontSize = 18.sp) }
                                    }
                                    Text(step.action, style = MaterialTheme.typography.titleLarge)
                                }
                                TextButton(
                                    onClick = { read(step.fullText) },
                                    enabled = speechReady(),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                ) {
                                    Text(
                                        stringResource(
                                            if (speechReady()) R.string.helper_result_read else R.string.helper_overlay_read_unavailable,
                                        ),
                                    )
                                }
                                if (live) {
                                    TextButton(
                                        onClick = { liveRefresh?.invoke() },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    ) {
                                        Text("다음 안내 요청")
                                    }
                                }
                            }
                        }
                        if (fits && !captionBelow) CaptionPointer(target.center.x - (width - captionWidth) / 2f, false)
                    }
                    if (captionBelow) controls()
                }
            }
        }
    }
}

@Composable
private fun CaptionPointer(
    targetX: Float,
    below: Boolean,
) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val x = targetX.coerceIn(24.dp.toPx(), size.width - 24.dp.toPx())
        val path =
            Path().apply {
                moveTo(x - size.height, if (below) size.height else 0f)
                lineTo(x, if (below) 0f else size.height)
                lineTo(x + size.height, if (below) size.height else 0f)
                close()
            }
        drawPath(path, LightColorScheme.surface)
    }
}

@Composable
private fun ConsequencePanel(
    step: ConsequenceStep,
    phase: ConsequencePhase,
    foreground: Boolean,
    live: Boolean,
    onRefresh: () -> Unit,
    menu: Boolean,
    maxHeight: Int,
    canLocate: Boolean,
    speechReady: Boolean,
    autoRead: Boolean,
    onAutoReadChange: (Boolean) -> Unit,
    onReturn: () -> Unit,
    onToggleMenu: () -> Unit,
    onLocate: () -> Unit,
    onRead: () -> Unit,
    onExplain: () -> Unit,
    onRetry: () -> Unit,
    onChange: () -> Unit,
) {
    Surface(
        color = LightColorScheme.surface,
        contentColor = LightColorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = maxHeight.dp)
                .semantics { paneTitle = if (menu) "도움 메뉴" else "버튼 결과 설명" },
    ) {
        Column(Modifier.padding(16.dp)) {
            HelperControlActions(
                onReturn = onReturn,
                secondaryLabel = if (menu) R.string.helper_overlay_menu_close else R.string.helper_overlay_menu,
                onSecondary = onToggleMenu,
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (menu) stringResource(R.string.helper_overlay_menu) else "누르면 어떻게 되나요?",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                if (menu) {
                    HelperMenuAction(R.string.helper_result_again, Icons.Rounded.ChatBubbleOutline, onExplain)
                    HelperMenuAction(R.string.helper_overlay_retry, Icons.Rounded.Refresh, onRetry)
                    if (live) {
                        TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 56.dp)) { Text("현재 화면 다시 확인") }
                    } else {
                        HelperMenuAction(R.string.helper_result_simulate, Icons.Rounded.OpenWith, onChange)
                    }
                } else {
                    if (phase == ConsequencePhase.RECONFIRM) {
                        Text(
                            "화면이나 정보가 바뀌었어요. 다시 확인해 주세요.",
                            fontSize = 18.sp,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Text(step.result, style = MaterialTheme.typography.titleLarge)
                    step.facts.forEach { Text(it, fontSize = 18.sp) }
                    Text("실제 실행 없이 버튼 위치만 안내해요.", fontSize = 18.sp)
                    if (!canLocate) {
                        Text(
                            if (!foreground) {
                                "테스트 화면을 벗어났어요. 테스트 화면으로 돌아오면 위치를 다시 확인해요."
                            } else {
                                "버튼과 함께 표시할 공간이 부족하거나 위치를 확인할 수 없어요. 화면 방향을 바꿔 주세요."
                            },
                            fontSize = 18.sp,
                        )
                    }
                }
                if (!menu) {
                    TextButton(
                        onClick = onRead,
                        enabled = speechReady,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text(
                            stringResource(if (speechReady) R.string.helper_result_read else R.string.helper_overlay_read_unavailable),
                            fontSize = 18.sp,
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = autoRead, role = Role.Switch, onValueChange = onAutoReadChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.helper_result_auto_read), Modifier.weight(1f), fontSize = 18.sp)
                    Switch(checked = autoRead, onCheckedChange = null)
                }
            }
            if (!menu) {
                Button(
                    onClick = if (live && !canLocate) onRefresh else onLocate,
                    enabled = canLocate || live,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Text(if (live && !canLocate) "다음 안내 요청" else "버튼 위치 보기", fontSize = 18.sp)
                }
            }
        }
    }
}
