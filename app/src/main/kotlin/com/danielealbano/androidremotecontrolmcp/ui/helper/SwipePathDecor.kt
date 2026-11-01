@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.ScreenPoint

private val DECOR_COLOR = Color.White

// The path guide: a black halo under every white stroke keeps it readable on light and dark screens.
private val HALO_COLOR = Color.Black.copy(alpha = 0.7f)
private val PATH_STROKE = 4.dp
private val PATH_HALO_EXTRA = 4.dp
private val RING_STROKE = 4.dp
private val HEAD_SIZE = 18.dp
private val TIP_INSET = 8.dp
private const val PATH_PERIOD_MS = 2_100
private const val PATH_PHASE_END = 1.35f
private const val PATH_LINE_ALPHA = 0.7f

/**
 * The ring around the swipe's start point [center] (screen pixels) that falls in this strip window at [part]: the
 * finger goes down in the uncovered gap inside it. [radius] is the ring's centre line.
 */
@Composable
internal fun SwipeStartRingPart(
    center: ScreenPoint,
    part: PxRect,
    radius: Int,
) {
    Canvas(Modifier.fillMaxSize()) {
        val at = Offset((center.x - part.left).toFloat(), (center.y - part.top).toFloat())
        val stroke = RING_STROKE.toPx()
        drawCircle(HALO_COLOR, radius.toFloat(), at, style = Stroke(stroke + PATH_HALO_EXTRA.toPx()))
        drawCircle(DECOR_COLOR, radius.toFloat(), at, style = Stroke(stroke))
    }
}

/**
 * The swipe path inside its window: a line from the window's start side to an arrowhead at the far end, and with
 * animations on an arrowhead travelling from the start to the end, then pausing. Static when animations are off.
 */
@Composable
internal fun SwipePathLine(direction: ScrollDirection) {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val phase = if (animate) pathPhase() else PATH_PHASE_STILL
    Canvas(Modifier.fillMaxSize()) {
        val vertical = direction == ScrollDirection.UP || direction == ScrollDirection.DOWN
        val forward = direction == ScrollDirection.DOWN || direction == ScrollDirection.RIGHT
        val sign = if (forward) 1f else -1f
        val along = if (vertical) size.height else size.width
        val mid = (if (vertical) size.width else size.height) / 2
        val inset = TIP_INSET.toPx()
        val startAt = if (forward) 0f else along
        val endAt = if (forward) along - inset else inset

        fun point(position: Float) = if (vertical) Offset(mid, position) else Offset(position, mid)
        val stroke = PATH_STROKE.toPx()
        val halo = stroke + PATH_HALO_EXTRA.toPx()
        drawLine(HALO_COLOR, point(startAt), point(endAt), halo, StrokeCap.Round)
        drawLine(DECOR_COLOR.copy(alpha = PATH_LINE_ALPHA), point(startAt), point(endAt), stroke, StrokeCap.Round)
        drawArrowHead(point(endAt), vertical, sign, 1f)
        if (animate && phase < 1f) {
            drawArrowHead(point(startAt + (endAt - startAt) * phase), vertical, sign, 1f)
        }
    }
}

private const val PATH_PHASE_STILL = 1f

@Composable
private fun pathPhase(): Float {
    val transition = rememberInfiniteTransition(label = "swipe-path")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = PATH_PHASE_END,
        animationSpec = infiniteRepeatable(tween(PATH_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "swipe-path-phase",
    )
    return phase.coerceAtMost(1f)
}

private fun DrawScope.drawArrowHead(
    tip: Offset,
    vertical: Boolean,
    sign: Float,
    alpha: Float,
) {
    val size = HEAD_SIZE.toPx()
    val path =
        Path().apply {
            if (vertical) {
                moveTo(tip.x - size, tip.y - sign * size)
                lineTo(tip.x, tip.y)
                lineTo(tip.x + size, tip.y - sign * size)
            } else {
                moveTo(tip.x - sign * size, tip.y - size)
                lineTo(tip.x, tip.y)
                lineTo(tip.x - sign * size, tip.y + size)
            }
        }
    val stroke = PATH_STROKE.toPx() + 1.dp.toPx()
    drawPath(path, HALO_COLOR, style = pathStroke(stroke + PATH_HALO_EXTRA.toPx()))
    drawPath(path, DECOR_COLOR.copy(alpha = alpha), style = pathStroke(stroke))
}

private fun pathStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
