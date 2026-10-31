package com.danielealbano.androidremotecontrolmcp.testutil

import kotlin.random.Random

/**
 * Seeded generators shared by the `*FuzzTest` property tests. Everything is derived from the caller's
 * [Random], so a failing case is reproducible from its seed and iteration number. Special characters are
 * produced from escapes, never stored raw in the sources.
 */
internal object FuzzStrings {
    private val SPECIAL_CHARS =
        charArrayOf(
            '\u0000',
            '\u0001',
            '\t',
            '\n',
            '\r',
            '\u000B',
            '\u000C',
            '\u001B',
            '\u001C',
            '\u001F',
            ' ',
            '\u007F',
            '\u0080',
            '\u0085',
            '\u009F',
            '\u00A0',
            '\u200B',
            '\u2028',
            '\u2029',
            '\uFEFF',
            '\uD800',
            '\uDBFF',
            '\uDC00',
            '\uDFFF',
            '"',
            '\\',
            '\'',
            '<',
            '>',
            '\u0130',
            '\u212A',
        )

    private val EMOJI =
        listOf(
            "\uD83D\uDE00",
            "\uD83C\uDDE9\uD83C\uDDEA",
            "\uD835\uDD38",
            "\uD83D\uDC68\u200D\uD83D\uDC69",
        )

    /** A random UTF-16 string that may contain controls, line separators, valid pairs and lone surrogates. */
    fun random(
        rnd: Random,
        maxLength: Int,
    ): String {
        val target = rnd.nextInt(maxLength + 1)
        val sb = StringBuilder()
        while (sb.length < target) {
            when (rnd.nextInt(PICK_RANGE)) {
                0, 1, 2, 3 -> sb.append(('a'.code + rnd.nextInt(ALPHABET_SIZE)).toChar())
                4 -> sb.append(SPECIAL_CHARS[rnd.nextInt(SPECIAL_CHARS.size)])
                5 -> sb.append(EMOJI[rnd.nextInt(EMOJI.size)])
                6 -> sb.append(rnd.nextInt(' '.code, MAX_BMP_NO_SURROGATE).toChar())
                else -> sb.append(rnd.nextInt(BYTE_LIMIT).toChar())
            }
        }
        return sb.toString()
    }

    /** Like [random] but guaranteed to be well-formed UTF-16 (no lone surrogates). */
    fun validUtf16(
        rnd: Random,
        maxLength: Int,
    ): String = dropLoneSurrogates(random(rnd, maxLength))

    fun dropLoneSurrogates(value: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate() -> {
                    sb.append(c).append(value[i + 1])
                    i++
                }

                c.isSurrogate() -> {
                    Unit
                }

                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        return sb.toString()
    }

    fun hasLoneSurrogate(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c.isHighSurrogate() && i + 1 < value.length && value[i + 1].isLowSurrogate()) {
                i += 2
                continue
            }
            if (c.isSurrogate()) return true
            i++
        }
        return false
    }

    /** Printable, escaped form of [value] for failure messages. */
    fun escape(value: String): String =
        buildString {
            for (c in value) {
                if (c.code in PRINTABLE_ASCII) {
                    append(c)
                } else {
                    append("\\u").append(c.code.toString(HEX_RADIX).padStart(HEX_WIDTH, '0'))
                }
            }
        }

    private val PRINTABLE_ASCII = 0x20..0x7E
    private const val PICK_RANGE = 8
    private const val ALPHABET_SIZE = 26
    private const val MAX_BMP_NO_SURROGATE = 0xD800
    private const val BYTE_LIMIT = 0x100
    private const val HEX_RADIX = 16
    private const val HEX_WIDTH = 4
}
