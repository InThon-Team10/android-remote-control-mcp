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

internal data class HelperCaptionPlacement(
    val left: Int,
    val top: Int,
    val below: Boolean,
    val fits: Boolean,
)

/** Prefer below, then above. For oversized captions use the roomier side and keep the top visible. */
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
    val top =
        (if (below) target.bottom + separation else target.top - separation - caption.height)
            .coerceIn(margin, maxOf(margin, screen.height - margin - caption.height))
    return HelperCaptionPlacement(
        left,
        top,
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
