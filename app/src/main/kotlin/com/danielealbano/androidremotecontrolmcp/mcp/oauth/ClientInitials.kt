package com.danielealbano.androidremotecontrolmcp.mcp.oauth

/**
 * Up to two leading characters of [name] (trimmed, uppercased) for a monogram avatar. Counts Unicode code
 * points, not UTF-16 units, so a surrogate pair at the cut point is never split into a lone surrogate
 * (which renders as a replacement character / `?`). Returns an empty string for a blank name.
 */
fun clientInitials(name: String): String =
    // Uppercasing can expand a letter (sharp s becomes "SS"), so the cap is applied again afterwards.
    name
        .trim()
        .takeCodePoints(INITIALS_LENGTH)
        .uppercase()
        .takeCodePoints(INITIALS_LENGTH)

private fun String.takeCodePoints(count: Int): String {
    val end = offsetByCodePoints(0, minOf(count, codePointCount(0, length)))
    return substring(0, end)
}

private const val INITIALS_LENGTH = 2
