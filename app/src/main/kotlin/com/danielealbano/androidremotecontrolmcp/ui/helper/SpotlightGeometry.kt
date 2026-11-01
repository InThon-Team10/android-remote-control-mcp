package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection
import com.danielealbano.androidremotecontrolmcp.services.guidance.ScreenPoint

/*
 * Nothing may ever cover the spotlight hole, not even an inert window: Android marks touches that pass through
 * another app's window as obscured, and apps such as the home screen or banking apps then ignore them. The outline
 * and the swipe arrow are therefore drawn in windows placed around the hole, never over it.
 */

/** The outline ring around [hole]: the hole grown by [thickness] on every side, limited to the screen. */
internal fun outlineRing(
    hole: PxRect,
    thickness: Int,
    screen: PxSize,
): PxRect =
    PxRect(
        (hole.left - thickness).coerceAtLeast(0),
        (hole.top - thickness).coerceAtLeast(0),
        (hole.right + thickness).coerceAtMost(screen.width),
        (hole.bottom + thickness).coerceAtMost(screen.height),
    )

/** The four strips of [ring] outside [hole] (top and bottom include the corners); empty strips are dropped. */
internal fun outlineStrips(
    hole: PxRect,
    ring: PxRect,
): List<PxRect> =
    listOf(
        PxRect(ring.left, ring.top, ring.right, hole.top),
        PxRect(ring.left, hole.bottom, ring.right, ring.bottom),
        PxRect(ring.left, hole.top, hole.left, hole.bottom),
        PxRect(hole.right, hole.top, ring.right, hole.bottom),
    ).filter { it.width > 0 && it.height > 0 }

/** Size of the centred swipe pill: [thickness] across the swipe, at most [length] along it. */
internal data class SwipePillSize(
    val thickness: Int,
    val length: Int,
)

/**
 * The compact swipe pill for scrolling a node (no start point): centred on [area], pointing [direction], always on
 * screen. The pill window is inert and the gesture reaches the app underneath.
 */
internal fun swipeArrowRect(
    area: PxRect,
    direction: ScrollDirection,
    size: SwipePillSize,
    screen: PxSize,
): PxRect {
    val vertical = direction.isVertical()
    val width = if (vertical) size.thickness else size.length
    val height = if (vertical) size.length else size.thickness
    val left = (area.centerX - width / 2).coerceIn(0, maxOf(0, screen.width - width))
    val top = ((area.top + area.bottom) / 2 - height / 2).coerceIn(0, maxOf(0, screen.height - height))
    return PxRect(left, top, left + width, top + height)
}

/**
 * Pixel sizes of the swipe path guide: an uncovered square of [gapHalf] around the start, a ring drawn in windows
 * around it out to [ringOuter], and a line window [lineHalf] either side of the path ending [endPad] past the end.
 * The line stops [endInset] short of the far edge of the swipe area (the margin the area was grown by).
 */
internal data class SwipePathMetrics(
    val gapHalf: Int,
    val ringOuter: Int,
    val lineHalf: Int,
    val endPad: Int,
    val endInset: Int,
    val minLine: Int,
)

/**
 * Windows of the swipe path guide. [gap] is never covered (it holds the start pixel); [ringStrips] surround it;
 * [line] begins at the gap's edge and runs along the swipe to the arrowhead (null when there is no room).
 */
internal data class SwipePathLayout(
    val gap: PxRect,
    val ring: PxRect,
    val ringStrips: List<PxRect>,
    val line: PxRect?,
)

/**
 * The path guide for a swipe from [start] in [direction] across [area]: the finger goes down inside
 * [SwipePathLayout.gap] (no window covers it, apps ignore a gesture whose down point lies under another window)
 * and moves along the line window to its far end. Everything is limited to [screen].
 */
internal fun swipePathLayout(
    area: PxRect,
    start: ScreenPoint,
    direction: ScrollDirection,
    metrics: SwipePathMetrics,
    screen: PxSize,
): SwipePathLayout {
    val gap =
        PxRect(
            (start.x - metrics.gapHalf).coerceAtLeast(0),
            (start.y - metrics.gapHalf).coerceAtLeast(0),
            (start.x + metrics.gapHalf).coerceAtMost(screen.width),
            (start.y + metrics.gapHalf).coerceAtMost(screen.height),
        )
    val ring = outlineRing(gap, metrics.ringOuter - metrics.gapHalf, screen)
    val line = lineWindow(area, start, direction, gap, metrics, screen)
    return SwipePathLayout(gap, ring, outlineStrips(gap, ring), line)
}

private fun lineWindow(
    area: PxRect,
    start: ScreenPoint,
    direction: ScrollDirection,
    gap: PxRect,
    metrics: SwipePathMetrics,
    screen: PxSize,
): PxRect? {
    val vertical = direction.isVertical()
    val forward = direction == ScrollDirection.DOWN || direction == ScrollDirection.RIGHT
    val limit = if (vertical) screen.height else screen.width
    val near = if (vertical) area.top else area.left
    val far = if (vertical) area.bottom else area.right
    val from = if (vertical) gap.bottom else gap.right
    val back = if (vertical) gap.top else gap.left
    val span =
        if (forward) {
            from to minOf((far - metrics.endInset).coerceAtMost(limit) + metrics.endPad, limit)
        } else {
            maxOf((near + metrics.endInset).coerceAtLeast(0) - metrics.endPad, 0) to back
        }
    if (span.second - span.first < metrics.minLine) return null
    val center = if (vertical) start.x else start.y
    val across = if (vertical) screen.width else screen.height
    val low = (center - metrics.lineHalf).coerceIn(0, maxOf(0, across - 2 * metrics.lineHalf))
    val high = minOf(low + 2 * metrics.lineHalf, across)
    return if (vertical) PxRect(low, span.first, high, span.second) else PxRect(span.first, low, span.second, high)
}

private fun ScrollDirection.isVertical(): Boolean = this == ScrollDirection.UP || this == ScrollDirection.DOWN
