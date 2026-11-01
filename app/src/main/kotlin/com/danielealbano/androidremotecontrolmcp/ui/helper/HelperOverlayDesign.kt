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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Text bubble: the instruction and, as a smaller second line, the explanation; the text scrolls when the bubble is
 * given less room than it needs. [pointerX] (px from its left edge) draws a tail toward the target, up when
 * [pointerUp].
 */
@Composable
internal fun CaptionBubble(
    caption: GuidanceCaption,
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
            Column(
                Modifier
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GuidanceDisplay.CAPTION_LINE_GAP_DP.dp),
            ) {
                Text(
                    caption.instruction,
                    style = MaterialTheme.typography.titleLarge.copy(platformStyle = PlatformTextStyle(includeFontPadding = true)),
                )
                caption.explanation?.let { explanation ->
                    Text(
                        explanation,
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = true),
                                fontSize = GuidanceDisplay.EXPLANATION_TEXT_SP.sp,
                                lineHeight = GuidanceDisplay.EXPLANATION_LINE_SP.sp,
                            ),
                    )
                }
            }
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

/**
 * Contents of a blocker window beside the hole: dim plus the caption and close button this band owns. The caption
 * hugs the hole inside its room ([SpotlightLayout.caption]) and scrolls when it needs more.
 */
@Composable
internal fun SpotlightBand(
    band: PxRect,
    layout: SpotlightLayout,
    side: BandSide?,
    caption: GuidanceCaption,
    holeCenterX: Int,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    DimLayer {
        if (layout.captionSide == side) {
            val margin = with(density) { 16.dp.roundToPx() }
            val top = if (layout.captionBelow) layout.caption.top else band.top + margin
            val bottom = if (layout.captionBelow) band.bottom - margin else layout.caption.bottom
            val height = maxOf(0, bottom - top)
            Box(
                Modifier
                    .offset { IntOffset(layout.caption.left - band.left, top - band.top) }
                    .size(with(density) { DpSize(layout.caption.width.toDp(), height.toDp()) }),
                contentAlignment = if (layout.captionBelow) Alignment.TopStart else Alignment.BottomStart,
            ) {
                TypeHintCaption(
                    caption,
                    if (layout.captionFits) holeCenterX - layout.caption.left else null,
                    layout.captionBelow,
                    with(density) { height.toDp() },
                    onClose,
                )
            }
        }
    }
}

/**
 * Caption window without dim: the bubble sits next to the target (pointer side), the close button opposite. At most
 * [maxHeight] tall: the close button stays whole and the bubble's text scrolls.
 */
@Composable
internal fun TypeHintCaption(
    caption: GuidanceCaption,
    pointerX: Int?,
    pointerUp: Boolean,
    maxHeight: Dp,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!pointerUp) GuidanceCloseButton(onClose, Modifier.align(Alignment.End))
        CaptionBubble(caption, Modifier.fillMaxWidth().weight(1f, fill = false), pointerX, pointerUp)
        if (pointerUp) GuidanceCloseButton(onClose, Modifier.align(Alignment.End))
    }
}
