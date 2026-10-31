package com.danielealbano.androidremotecontrolmcp.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("escapeControlCharacters")
class EscapeControlCharactersTest {
    private val backslash = Char(BACKSLASH_CODE)

    @Test
    fun `normal text is returned unchanged`() {
        val text = "dir/file é 😀 name.txt"
        assertSame(text, text.escapeControlCharacters())
    }

    @Test
    fun `the full set of control and line separator characters is escaped`() {
        val codes = listOf(0x00, 0x09, 0x0A, 0x0D, 0x1B, 0x7F, 0x85, 0x9F, 0x2028, 0x2029)
        for (code in codes) {
            val expected = "a" + backslash + "u" + code.toString(HEX_RADIX).padStart(HEX_WIDTH, '0') + "b"
            assertEquals(expected, ("a" + Char(code) + "b").escapeControlCharacters())
        }
    }

    @Test
    fun `an empty string stays empty`() {
        assertEquals("", "".escapeControlCharacters())
    }

    private companion object {
        const val BACKSLASH_CODE = 92
        const val HEX_RADIX = 16
        const val HEX_WIDTH = 4
    }
}
