package com.danielealbano.androidremotecontrolmcp.mcp.oauth

/**
 * Up to two leading characters of [name] (trimmed, uppercased) for a monogram avatar. Counts Unicode code
 * points, not UTF-16 units, so a surrogate pair at the cut point is never split into a lone surrogate
 * (which renders as a replacement character / `?`). Returns an empty string for a blank name.
 */
fun clientInitials(name: String): String {
    val trimmed = name.trim()
    val end = trimmed.offsetByCodePoints(0, minOf(INITIALS_LENGTH, trimmed.codePointCount(0, trimmed.length)))
    return trimmed.substring(0, end).uppercase()
}

private const val INITIALS_LENGTH = 2
