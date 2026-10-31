package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("clientInitials")
class ClientInitialsTest {
    private val grin = "😀"

    @Test
    @DisplayName("uppercases the first two characters of a plain name")
    fun plainName() {
        assertEquals("CL", clientInitials("claude"))
    }

    @Test
    @DisplayName("trims surrounding whitespace before taking initials")
    fun trimsWhitespace() {
        assertEquals("AB", clientInitials("  ab"))
    }

    @Test
    @DisplayName("returns empty for blank input")
    fun blankInput() {
        assertEquals("", clientInitials("   "))
    }

    @Test
    @DisplayName("does not split a surrogate pair at the cut point")
    fun keepsSurrogatePairWhole() {
        assertEquals("A$grin", clientInitials("A${grin}B"))
    }

    @Test
    @DisplayName("keeps two emoji whole")
    fun twoEmoji() {
        assertEquals("$grin$grin", clientInitials("$grin${grin}x"))
    }

    @Test
    @DisplayName("never yields a lone surrogate")
    fun neverLoneSurrogate() {
        val result = clientInitials("x${grin}y")
        val encoded = String(result.toByteArray(Charsets.UTF_8), Charsets.UTF_8)
        assertEquals(result, encoded)
        assertFalse(result.contains('?'))
    }
}
