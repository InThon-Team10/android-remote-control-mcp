@file:Suppress("FunctionNaming", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

/** Blocks background touches only while a real request is running. */
@Composable
internal fun HelperThinkingOverlay(onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            .pointerInput(Unit) { detectTapGestures {} }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End).heightIn(min = 56.dp)) {
            Text(stringResource(R.string.helper_thinking_cancel), color = Color.White, fontSize = 18.sp)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            ThinkingMascot()
            Text(
                stringResource(R.string.helper_thinking_title),
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                stringResource(R.string.helper_thinking_body),
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
    val dotCount =
        if (ValueAnimator.areAnimatorsEnabled()) {
            val transition = rememberInfiniteTransition(label = "thinking dots")
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 4f,
                animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing)),
                label = "one two three blank",
            )
            (phase.toInt() + 1) % 4
        } else {
            3
        }
    Box(Modifier.widthIn(max = 200.dp).fillMaxWidth().aspectRatio(1f)) {
        Image(painterResource(R.drawable.helper_phone_mascot_thinking), null, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            // Coordinates normalized to the empty cloud in the square mascot asset.
            repeat(dotCount) { index ->
                drawCircle(
                    color = Color(0xFF07134B),
                    radius = size.width * 0.015f,
                    center = Offset(size.width * (0.759f + index * 0.045f), size.height * 0.187f),
                )
            }
        }
    }
}
