@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.danielealbano.androidremotecontrolmcp.ui.theme.PrimaryContainerLight

private val VISUAL_SIZE = 208.dp
private val CHARACTER_SIZE = 176.dp
private val OUTER_RING_STROKE = 3.dp
private val INNER_RING_STROKE = 2.dp
private const val DISC_RADIUS = 0.44f
private const val OUTER_RING_RADIUS = 0.48f
private const val INNER_RING_RADIUS = 0.40f
private const val OUTER_RING_ALPHA = 0.35f
private const val INNER_RING_ALPHA = 0.7f
private const val BREATH_MIN_SCALE = 0.93f
private const val BREATH_MS = 2_800

/**
 * 콕콕이 on the home screen. [awake] shows the bright disc with two breathing rings (a still disc when the system
 * removed animations); asleep shows the sleeping character alone.
 */
@Composable
internal fun HelperMascotStateVisual(awake: Boolean) {
    val scale = if (awake) breathingScale() else 1f
    val ring = MaterialTheme.colorScheme.primary
    Box(Modifier.size(VISUAL_SIZE), contentAlignment = Alignment.Center) {
        if (awake) {
            Canvas(Modifier.size(VISUAL_SIZE)) {
                val side = size.minDimension
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(PrimaryContainerLight, DISC_RADIUS * side, center)
                drawCircle(
                    ring.copy(alpha = OUTER_RING_ALPHA),
                    OUTER_RING_RADIUS * side * scale,
                    center,
                    style = Stroke(OUTER_RING_STROKE.toPx()),
                )
                drawCircle(
                    ring.copy(alpha = INNER_RING_ALPHA),
                    INNER_RING_RADIUS * side * scale,
                    center,
                    style = Stroke(INNER_RING_STROKE.toPx()),
                )
            }
        }
        HelperCharacter(CHARACTER_SIZE, state = if (awake) HelperMascotState.AWAKE else HelperMascotState.SLEEPING)
    }
}

@Composable
private fun breathingScale(): Float {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = remember(lifecycleState) { ValueAnimator.areAnimatorsEnabled() }
    if (!animate) return 1f
    val transition = rememberInfiniteTransition(label = "breath")
    val scale by transition.animateFloat(
        initialValue = BREATH_MIN_SCALE,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathScale",
    )
    return scale
}
