package com.danielealbano.androidremotecontrolmcp.ui.helper

import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScrollDirection

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

/**
 * The compact swipe pill for a swipe in [direction] over [area]: [thickness] across and [length] along the swipe
 * axis, centred on the area and kept on screen. The pill window is inert; the swipe reaches the app underneath.
 */
internal fun swipeArrowRect(
    area: PxRect,
    direction: ScrollDirection,
    thickness: Int,
    length: Int,
    screen: PxSize,
): PxRect {
    val vertical = direction == ScrollDirection.UP || direction == ScrollDirection.DOWN
    val width = if (vertical) thickness else length
    val height = if (vertical) length else thickness
    val left = (area.centerX - width / 2).coerceIn(0, maxOf(0, screen.width - width))
    val top = ((area.top + area.bottom) / 2 - height / 2).coerceIn(0, maxOf(0, screen.height - height))
    return PxRect(left, top, left + width, top + height)
}
