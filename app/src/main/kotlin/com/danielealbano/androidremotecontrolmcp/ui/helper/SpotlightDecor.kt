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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection

private val DECOR_COLOR = Color.White
private const val GLOW_ALPHA = 0.35f
private val LINE_STROKE = 3.dp
private val HOLE_CORNER = 10.dp
private val CHEVRON_STROKE = 5.dp

// The black pill gives the white chevrons the same strong contrast on light and dark screens.
private val PILL_COLOR = Color.Black.copy(alpha = 0.82f)
private val PILL_PADDING = 10.dp
private const val CHEVRONS = 3
private const val CHEVRON_DEPTH = 0.32f
private const val CHEVRON_SPAN = 0.7f
private const val ARROW_PERIOD_MS = 1_100
private const val STILL_PROGRESS = 0.5f

/**
 * The part of the outline [ring] that falls in this strip window at [part] (screen pixels): a soft glow and a line
 * hugging the hole from outside, [thickness] pixels wide. Inert and decorative.
 */
@Composable
internal fun SpotlightRingPart(
    ring: PxRect,
    part: PxRect,
    thickness: Int,
) {
    Canvas(Modifier.fillMaxSize()) {
        translate((ring.left - part.left).toFloat(), (ring.top - part.top).toFloat()) {
            drawRing(Size(ring.width.toFloat(), ring.height.toFloat()), thickness.toFloat())
        }
    }
}

private fun DrawScope.drawRing(
    ring: Size,
    thickness: Float,
) {
    val corner = HOLE_CORNER.toPx()
    val glowInset = thickness / 2
    drawRoundRect(
        DECOR_COLOR.copy(alpha = GLOW_ALPHA),
        Offset(glowInset, glowInset),
        Size(ring.width - thickness, ring.height - thickness),
        CornerRadius(corner + glowInset),
        style = Stroke(thickness),
    )
    val line = LINE_STROKE.toPx()
    val lineInset = thickness - line / 2
    drawRoundRect(
        DECOR_COLOR,
        Offset(lineInset, lineInset),
        Size(ring.width - 2 * lineInset, ring.height - 2 * lineInset),
        CornerRadius(corner),
        style = Stroke(line),
    )
}

/** A compact black pill with white chevrons sliding in [direction]: the swipe the user should make. */
@Composable
internal fun SwipeArrow(direction: ScrollDirection) {
    // With the system "Remove animations" setting the chevrons stand still, fully visible, instead of sliding.
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val progress = if (animate) slidingProgress() else STILL_PROGRESS
    Canvas(
        Modifier
            .fillMaxSize()
            .background(PILL_COLOR, CircleShape)
            .padding(PILL_PADDING),
    ) {
        val vertical = direction == ScrollDirection.UP || direction == ScrollDirection.DOWN
        val along = if (vertical) size.height else size.width
        val across = if (vertical) size.width else size.height
        val spacing = along / CHEVRONS
        repeat(CHEVRONS) { index ->
            val step = (index + progress) / CHEVRONS
            val alpha = if (animate) 1f - kotlin.math.abs(step * 2 - 1f) else 1f
            drawChevron(direction, step * along, across, spacing * CHEVRON_DEPTH * 2, alpha)
        }
    }
}

@Composable
private fun slidingProgress(): Float {
    val transition = rememberInfiniteTransition(label = "swipe-arrow")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(ARROW_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "swipe-arrow-progress",
    )
    return progress
}

private fun DrawScope.drawChevron(
    direction: ScrollDirection,
    position: Float,
    across: Float,
    depth: Float,
    alpha: Float,
) {
    val half = across * CHEVRON_SPAN / 2
    val mid = across / 2
    val path =
        Path().apply {
            when (direction) {
                ScrollDirection.UP -> {
                    val tip = size.height - position
                    chevron(mid - half, tip + depth, mid, tip, mid + half)
                }

                ScrollDirection.DOWN -> {
                    chevron(mid - half, position - depth, mid, position, mid + half)
                }

                ScrollDirection.LEFT -> {
                    val tip = size.width - position
                    chevronSideways(tip + depth, mid - half, tip, mid + half)
                }

                ScrollDirection.RIGHT -> {
                    chevronSideways(position - depth, mid - half, position, mid + half)
                }
            }
        }
    drawPath(path, DECOR_COLOR.copy(alpha = alpha), style = chevronStroke(CHEVRON_STROKE.toPx()))
}

/** A vertical chevron: from ([left], [baseY]) to the tip ([tipX], [tipY]) and back down to ([right], [baseY]). */
private fun Path.chevron(
    left: Float,
    baseY: Float,
    tipX: Float,
    tipY: Float,
    right: Float,
) {
    moveTo(left, baseY)
    lineTo(tipX, tipY)
    lineTo(right, baseY)
}

/** A horizontal chevron: from ([baseX], [top]) to the tip ([tipX], middle) and back to ([baseX], [bottom]). */
private fun Path.chevronSideways(
    baseX: Float,
    top: Float,
    tipX: Float,
    bottom: Float,
) {
    moveTo(baseX, top)
    lineTo(tipX, (top + bottom) / 2)
    lineTo(baseX, bottom)
}

private fun chevronStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
