package com.danielealbano.androidremotecontrolmcp.ui.helper

internal data class HelperCaptionPlacement(
    val left: Int,
    val top: Int,
    val below: Boolean,
    val fits: Boolean,
)

/** Prefer below, then above. For oversized captions use the roomier side and keep the top visible. */
@Suppress("LongParameterList")
internal fun placeHelperCaption(
    width: Int,
    height: Int,
    targetLeft: Int,
    targetTop: Int,
    targetRight: Int,
    targetBottom: Int,
    captionWidth: Int,
    captionHeight: Int,
    margin: Int,
    separation: Int,
): HelperCaptionPlacement {
    val belowSpace = height - margin - targetBottom - separation
    val aboveSpace = targetTop - separation - margin
    val below = belowSpace >= captionHeight || (aboveSpace < captionHeight && belowSpace >= aboveSpace)
    val left =
        ((targetLeft + targetRight - captionWidth) / 2)
            .coerceIn(margin, maxOf(margin, width - margin - captionWidth))
    val top =
        (if (below) targetBottom + separation else targetTop - separation - captionHeight)
            .coerceIn(margin, maxOf(margin, height - margin - captionHeight))
    return HelperCaptionPlacement(
        left,
        top,
        below,
        captionWidth <= width - margin * 2 && (belowSpace >= captionHeight || aboveSpace >= captionHeight),
    )
}

/** Keep controls opposite the target, trying the other side before using an explanation panel. */
internal fun helperControlsTop(
    height: Int,
    targetTop: Int,
    targetBottom: Int,
    controlsHeight: Int,
    margin: Int,
    preferTop: Boolean,
): Int? {
    val topFits = margin + controlsHeight + margin <= targetTop
    val bottomFits = height - margin - controlsHeight - margin >= targetBottom
    return when {
        preferTop && topFits -> margin
        bottomFits -> height - margin - controlsHeight
        topFits -> margin
        else -> null
    }
}
