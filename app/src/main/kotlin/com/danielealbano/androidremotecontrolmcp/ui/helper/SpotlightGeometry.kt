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

/** Size of the swipe pill: [thickness] across the swipe, at most [length] along it, [gap] past the swipe's start. */
internal data class SwipePillSize(
    val thickness: Int,
    val length: Int,
    val gap: Int,
)

/**
 * The compact swipe pill for a swipe in [direction]. With the swipe's [start] the pill begins [SwipePillSize.gap]
 * after it along the swipe and is centred on it across, so it shows where the finger goes down without covering
 * that point: at the screen edge it is shortened, never moved back over [start] (null when not even a pill as long
 * as it is thick fits). Without a start (scrolling a node) it is centred on [area]. Always on screen; the pill window
 * is inert and the swipe reaches the app underneath.
 */
internal fun swipeArrowRect(
    area: PxRect,
    start: ScreenPoint?,
    direction: ScrollDirection,
    size: SwipePillSize,
    screen: PxSize,
): PxRect? =
    if (start == null) {
        centredSwipePill(area, direction, size, screen)
    } else {
        anchoredSwipePill(start, direction, size, screen)
    }

private fun centredSwipePill(
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

private fun anchoredSwipePill(
    start: ScreenPoint,
    direction: ScrollDirection,
    size: SwipePillSize,
    screen: PxSize,
): PxRect? {
    val forward = direction == ScrollDirection.DOWN || direction == ScrollDirection.RIGHT
    return if (direction.isVertical()) {
        spanPastStart(start.y, forward, size, screen.height)?.let { (top, bottom) ->
            val left = centredAcross(start.x, size.thickness, screen.width)
            PxRect(left, top, left + size.thickness, bottom)
        }
    } else {
        spanPastStart(start.x, forward, size, screen.width)?.let { (left, right) ->
            val top = centredAcross(start.y, size.thickness, screen.height)
            PxRect(left, top, right, top + size.thickness)
        }
    }
}

/**
 * The pill's [low, high) span along the swipe axis: from the gap past [start] onward ([forward] = increasing
 * coordinates), at most the pill length and within [0, limit). Null when it would be shorter than the pill is thick.
 */
private fun spanPastStart(
    start: Int,
    forward: Boolean,
    size: SwipePillSize,
    limit: Int,
): Pair<Int, Int>? {
    val gap = size.gap.coerceAtLeast(1)
    val span =
        if (forward) {
            (start + gap) to minOf(start + gap + size.length, limit)
        } else {
            maxOf(start - gap - size.length, 0) to (start - gap)
        }
    return span.takeIf { it.second - it.first >= minOf(size.thickness, size.length) }
}

/** Where the pill begins across the swipe: centred on [center], kept within [0, limit). */
private fun centredAcross(
    center: Int,
    thickness: Int,
    limit: Int,
): Int = (center - thickness / 2).coerceIn(0, maxOf(0, limit - thickness))

private fun ScrollDirection.isVertical(): Boolean = this == ScrollDirection.UP || this == ScrollDirection.DOWN
