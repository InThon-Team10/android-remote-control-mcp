@file:Suppress("FunctionNaming", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

/**
 * Visual scrim only. [target] uses pixels relative to this canvas, not screen coordinates.
 * Cross-app touch routing belongs to the overlay window host, not this composable.
 */
@Composable
fun SpotlightOverlay(
    target: Rect?,
    modifier: Modifier = Modifier,
) {
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)
    Canvas(modifier = modifier) {
        // Keep the screen clear until detection supplies a visible region.
        val visibleTarget = clipHighlight(target, Rect(0f, 0f, size.width, size.height))
        if (visibleTarget != null) {
            val mask =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRect(visibleTarget)
                }
            drawPath(mask, scrim)
        }
    }
}

/** Ignore missing, invalid or off-screen detection results rather than dimming everything. */
internal fun clipHighlight(
    target: Rect?,
    viewport: Rect,
): Rect? {
    if (target == null || !listOf(target.left, target.top, target.right, target.bottom).all { it.isFinite() }) {
        return null
    }
    return target.intersect(viewport).takeIf { it.width > 0f && it.height > 0f }
}
