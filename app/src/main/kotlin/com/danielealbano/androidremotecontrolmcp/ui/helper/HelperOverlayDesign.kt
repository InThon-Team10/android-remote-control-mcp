@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme

internal const val DIM_ALPHA = 0.72f
private val DIM_COLOR = Color.Black.copy(alpha = DIM_ALPHA)
private val BUBBLE_SHAPE = RoundedCornerShape(24.dp)
private val TAIL_HEIGHT = 8.dp
private val TAIL_MIN_INSET = 24.dp
private val MIN_TOUCH = 56.dp

/** Overlay content theme: follows the system dark mode, never the wallpaper colours. */
@Composable
internal fun GuidanceTheme(content: @Composable () -> Unit) {
    AndroidRemoteControlMcpTheme(dynamicColor = false, content = content)
}

/** Dim layer of one blocker window; the optional [content] sits on top of it. */
@Composable
internal fun DimLayer(content: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(DIM_COLOR), content = content)
}

/** Text bubble; [pointerX] (px from its left edge) draws a tail toward the target, up when [pointerUp]. */
@Composable
internal fun CaptionBubble(
    text: String,
    modifier: Modifier = Modifier,
    pointerX: Int? = null,
    pointerUp: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(
            shape = BUBBLE_SHAPE,
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outline),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.titleLarge,
                modifier =
                    Modifier
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (pointerX != null) {
            val surface = colors.surface
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(TAIL_HEIGHT)
                    .align(if (pointerUp) Alignment.TopStart else Alignment.BottomStart)
                    .offset(y = if (pointerUp) -TAIL_HEIGHT + 1.dp else TAIL_HEIGHT - 1.dp),
            ) {
                val x = pointerX.toFloat().coerceIn(TAIL_MIN_INSET.toPx(), size.width - TAIL_MIN_INSET.toPx())
                val baseY = if (pointerUp) size.height else 0f
                val tipY = if (pointerUp) 0f else size.height
                val path =
                    Path().apply {
                        moveTo(x - size.height, baseY)
                        lineTo(x, tipY)
                        lineTo(x + size.height, baseY)
                        close()
                    }
                drawPath(path, surface)
            }
        }
    }
}

@Composable
internal fun GuidanceCloseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.helper_overlay_guide_close_description)
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MIN_TOUCH).semantics { contentDescription = description },
    ) {
        Text(stringResource(R.string.helper_overlay_guide_close), style = MaterialTheme.typography.titleMedium)
    }
}

/** Contents of a blocker window beside the hole: dim plus the caption and close button this band owns. */
@Composable
internal fun SpotlightBand(
    band: PxRect,
    layout: SpotlightLayout,
    side: BandSide?,
    caption: String,
    holeCenterX: Int,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    DimLayer {
        if (layout.captionSide == side) {
            val bubble = layout.caption
            CaptionBubble(
                caption,
                modifier =
                    Modifier
                        .offset { IntOffset(bubble.left - band.left, bubble.top - band.top) }
                        .width(with(density) { bubble.width.toDp() }),
                pointerX = if (layout.captionFits) holeCenterX - bubble.left else null,
                pointerUp = layout.captionBelow,
            )
        }
        if (layout.closeSide == side) {
            GuidanceCloseButton(
                onClose,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-16.dp.roundToPx(), layout.closeTop - band.top) },
            )
        }
    }
}

/** Caption window without dim: the bubble sits next to the target (pointer side), the close button opposite. */
@Composable
internal fun TypeHintCaption(
    caption: String,
    pointerX: Int?,
    pointerUp: Boolean,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!pointerUp) GuidanceCloseButton(onClose, Modifier.align(Alignment.End))
        CaptionBubble(caption, Modifier.fillMaxWidth(), pointerX, pointerUp)
        if (pointerUp) GuidanceCloseButton(onClose, Modifier.align(Alignment.End))
    }
}

/** Version A: explains what the action will do before the target is shown. */
@Composable
internal fun ExplanationPanel(
    text: String,
    maxHeight: Dp,
    onShowTarget: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val pane = stringResource(R.string.helper_overlay_explanation_pane)
    Surface(
        shape = BUBBLE_SHAPE,
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outline),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .heightIn(max = maxHeight)
                .semantics { paneTitle = pane },
    ) {
        Column(Modifier.padding(16.dp)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.helper_overlay_explanation_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(onShowTarget, Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH).padding(top = 8.dp)) {
                Text(stringResource(R.string.helper_overlay_show_target), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClose, Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH)) {
                Text(stringResource(R.string.helper_overlay_guide_close), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
