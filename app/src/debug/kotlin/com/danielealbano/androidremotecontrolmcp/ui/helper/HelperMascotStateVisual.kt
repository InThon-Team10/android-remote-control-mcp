@file:Suppress("FunctionNaming", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R

/** Decorative state companion; the home heading supplies the explicit on/off state. */
@Composable
internal fun HelperMascotStateVisual(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(208.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            // Compose's infinite transition also observes system animation duration changes.
            val ringScale =
                if (ValueAnimator.areAnimatorsEnabled()) {
                    val transition = rememberInfiniteTransition(label = "helper awake")
                    val scale by transition.animateFloat(
                        initialValue = 0.93f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(2800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        label = "gentle halo expansion",
                    )
                    scale
                } else {
                    1f
                }
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.primaryContainer, radius = size.minDimension * 0.44f)
                drawCircle(
                    colors.primary.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.48f * ringScale,
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawCircle(
                    colors.primary.copy(alpha = 0.7f),
                    radius = size.minDimension * 0.40f * ringScale,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Image(
            painter = painterResource(if (visible) R.drawable.helper_phone_mascot else R.drawable.helper_phone_mascot_sleeping),
            contentDescription = null,
            modifier = Modifier.size(176.dp),
        )
    }
}
