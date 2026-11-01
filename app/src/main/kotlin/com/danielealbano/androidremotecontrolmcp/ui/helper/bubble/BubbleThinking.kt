@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R

private const val SCRIM_ALPHA = 0.84f
private const val DOT_COLOR = 0xFF07134B
private const val DOT_RADIUS_FRACTION = 0.015f
private const val DOT_FIRST_X_FRACTION = 0.759f
private const val DOT_STEP_FRACTION = 0.045f
private const val DOT_Y_FRACTION = 0.187f
private const val THINKING_STEPS = 4
private const val THINKING_CYCLE_MS = 2400
private const val STATIC_DOT_COUNT = 3
private val DOT_COUNTS = intArrayOf(1, 2, 3, 0)
private val MASCOT_MAX_SIZE = 200.dp

/**
 * The full-screen thinking screen: 콕콕이 on a dark scrim until the first guidance step of a request. Shown in a
 * touchable window, so the touches it swallows need no extra modifier.
 */
@Composable
internal fun BubbleThinkingWindow(actions: BubbleActions) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = actions::onCancelThinking, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    text = stringResource(R.string.helper_thinking_cancel),
                    color = Color.White,
                    fontSize = 18.sp,
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ThinkingMascot()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.helper_thinking_title),
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.helper_thinking_body),
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ThinkingMascot() {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val dots = if (animate) animatedDotCount() else STATIC_DOT_COUNT
    Box(Modifier.widthIn(max = MASCOT_MAX_SIZE).fillMaxWidth().aspectRatio(1f)) {
        Image(
            painter = painterResource(R.drawable.helper_phone_mascot_thinking),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
        Canvas(Modifier.matchParentSize()) {
            repeat(dots) { index ->
                drawCircle(
                    color = Color(DOT_COLOR),
                    radius = DOT_RADIUS_FRACTION * size.width,
                    center =
                        Offset(
                            x = (DOT_FIRST_X_FRACTION + DOT_STEP_FRACTION * index) * size.width,
                            y = DOT_Y_FRACTION * size.width,
                        ),
                )
            }
        }
    }
}

/** Cycles 1, 2, 3, 0 dots, one step per 0.6 s. */
@Composable
private fun animatedDotCount(): Int {
    val phase by
        rememberInfiniteTransition(label = "thinking-dots").animateFloat(
            initialValue = 0f,
            targetValue = THINKING_STEPS.toFloat(),
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = THINKING_CYCLE_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "thinking-dots-phase",
        )
    return DOT_COUNTS[phase.toInt().coerceIn(0, THINKING_STEPS - 1)]
}
