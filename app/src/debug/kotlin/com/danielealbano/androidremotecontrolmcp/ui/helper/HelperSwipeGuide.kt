@file:Suppress("FunctionNaming", "MagicNumber", "LongParameterList")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class SwipeDirection(
    val label: String,
) {
    UP("위로"),
    DOWN("아래로"),
    LEFT("왼쪽으로"),
    RIGHT("오른쪽으로"),
}

internal enum class SwipeDistance(
    val fraction: Float,
    val label: String,
) {
    SHORT(0.25f, "짧게"),
    MEDIUM(0.5f, "적당히"),
    LONG(0.8f, "길게"),
}

internal data class SwipeGuide(
    val direction: SwipeDirection,
    val distance: SwipeDistance,
) {
    val instruction: String get() = direction.label + " " + distance.label + "\n밀어 주세요"
}

internal data class SwipePath(
    val start: Offset,
    val end: Offset,
) {
    val length: Float get() = (end - start).getDistance()
}

/** The path is a demonstration distance, never a promised scroll destination. */
internal fun swipePath(
    guide: SwipeGuide,
    region: Rect,
    viewport: Rect,
    top: Float,
    margin: Float,
): SwipePath? {
    if (!listOf(region.left, region.top, region.right, region.bottom, top, margin).all { it.isFinite() }) return null
    val visible = region.intersect(viewport).intersect(Rect(viewport.left, top, viewport.right, viewport.bottom))
    val area = Rect(visible.left + margin, visible.top + margin, visible.right - margin, visible.bottom - margin)
    if (area.width < margin * 2 || area.height < margin * 2) return null
    val vertical = guide.direction == SwipeDirection.UP || guide.direction == SwipeDirection.DOWN
    val half = (if (vertical) area.height else area.width) * guide.distance.fraction / 2f
    if (half < margin / 2f) return null
    val delta =
        when (guide.direction) {
            SwipeDirection.UP -> Offset(0f, -half)
            SwipeDirection.DOWN -> Offset(0f, half)
            SwipeDirection.LEFT -> Offset(-half, 0f)
            SwipeDirection.RIGHT -> Offset(half, 0f)
        }
    return SwipePath(area.center - delta, area.center + delta)
}

@Composable
internal fun SwipeGuideCanvas(path: SwipePath?) {
    val progress =
        if (path != null && ValueAnimator.areAnimatorsEnabled()) {
            val transition = rememberInfiniteTransition(label = "swipe demonstration")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing)),
                label = "start to end then pause",
            )
            phase.coerceAtMost(1f)
        } else {
            1f
        }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color.Black.copy(alpha = 0.78f))
        if (path == null) return@Canvas
        val delta = path.end - path.start
        val unit = delta / path.length
        val normal = Offset(-unit.y, unit.x)
        drawLine(Color.White.copy(alpha = 0.65f), path.start, path.end, 4.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White, 10.dp.toPx(), path.start, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, 4.dp.toPx(), path.end)
        val tip = path.start + delta * progress
        val tail = tip - unit * 15.dp.toPx()
        drawLine(Color.White, tail + normal * 10.dp.toPx(), tip, 5.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White, tail - normal * 10.dp.toPx(), tip, 5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
internal fun SwipeGuideCaption(
    guide: SwipeGuide,
    available: Boolean,
    onReturn: () -> Unit,
    onRead: (() -> Unit)?,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.background(Color(0xFF242429))) {
        TextButton(onClick = onReturn, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("도움 버튼으로 돌아가기", color = Color.White, fontSize = 18.sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(if (available) guide.instruction else "경로를 표시할 공간이 부족해요", color = Color.White, fontSize = 24.sp, lineHeight = 32.sp)
            Text(
                if (available) "동그라미에서 시작해 흰 선 끝까지 손가락을 움직여 보세요." else "화면 방향을 바꾸거나 다음 안내를 요청해 주세요.",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            )
            if (onRead != null) {
                TextButton(onClick = onRead, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("안내 듣기", color = Color.White, fontSize = 18.sp)
                }
            }
            if (onRefresh != null) {
                TextButton(onClick = onRefresh, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("다음 안내 요청", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

/** Debug preview uses the same drawing and caption without network or dispatched gestures. */
@Composable
internal fun SwipeGuidePreview(
    guide: SwipeGuide,
    onClose: () -> Unit,
) {
    DisposableEffect(Unit) {
        HelperRuntime.suppress(true)
        onDispose { HelperRuntime.suppress(false) }
    }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val density = LocalDensity.current
        val viewport = with(density) { Rect(0f, 0f, maxWidth.toPx(), maxHeight.toPx()) }
        var captionHeight by remember { mutableStateOf(with(density) { 300.dp.toPx() }) }
        val path = swipePath(guide, viewport, viewport, captionHeight, with(density) { 24.dp.toPx() })
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text("동작 미리보기 · 실제 조작 없음", fontSize = 18.sp)
            listOf("연습용 설정", "연결", "소리 및 진동", "알림", "화면", "배터리").forEach {
                Text(it, modifier = Modifier.padding(vertical = 32.dp), fontSize = 28.sp)
            }
        }
        SwipeGuideCanvas(path)
        SwipeGuideCaption(
            guide,
            path != null,
            onClose,
            null,
            null,
            Modifier.heightIn(max = maxHeight * 0.45f).onSizeChanged { captionHeight = it.height.toFloat() },
        )
    }
}
