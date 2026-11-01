package com.danielealbano.androidremotecontrolmcp.ui.helper

internal data class PxSize(
    val width: Int,
    val height: Int,
)

internal data class PxRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
}

/**
 * The caption's [left] edge and the room it may fill on its side of the target, from [top] to [bottom] (empty when
 * that side has none). [fits] when the estimated caption fits in that room.
 */
internal data class HelperCaptionPlacement(
    val left: Int,
    val top: Int,
    val bottom: Int,
    val below: Boolean,
    val fits: Boolean,
) {
    val height: Int get() = bottom - top
}

/**
 * Prefer below, then above; when neither side has room for the whole [caption], the roomier side. The caption's room
 * runs from the target ([separation] away) to the screen edge ([margin] away), so it never covers the target and is
 * never cut off by the screen edge: a caption taller than its room scrolls.
 */
internal fun placeHelperCaption(
    screen: PxSize,
    target: PxRect,
    caption: PxSize,
    margin: Int,
    separation: Int,
): HelperCaptionPlacement {
    val belowSpace = screen.height - margin - target.bottom - separation
    val aboveSpace = target.top - separation - margin
    val below = belowSpace >= caption.height || (aboveSpace < caption.height && belowSpace >= aboveSpace)
    val left =
        ((target.left + target.right - caption.width) / 2)
            .coerceIn(margin, maxOf(margin, screen.width - margin - caption.width))
    val top = if (below) target.bottom + separation else margin
    val bottom = if (below) screen.height - margin else target.top - separation
    return HelperCaptionPlacement(
        left,
        top,
        maxOf(top, bottom),
        below,
        caption.width <= screen.width - margin * 2 && (belowSpace >= caption.height || aboveSpace >= caption.height),
    )
}

/** Keep controls opposite the target, trying the other side before giving up (null). */
internal fun helperControlsTop(
    screenHeight: Int,
    target: PxRect,
    controlsHeight: Int,
    margin: Int,
    preferTop: Boolean,
): Int? {
    val topFits = margin + controlsHeight + margin <= target.top
    val bottomFits = screenHeight - margin - controlsHeight - margin >= target.bottom
    return when {
        preferTop && topFits -> margin
        bottomFits -> screenHeight - margin - controlsHeight
        topFits -> margin
        else -> null
    }
}
