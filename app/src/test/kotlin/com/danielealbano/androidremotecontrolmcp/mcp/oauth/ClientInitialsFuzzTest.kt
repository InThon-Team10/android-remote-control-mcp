package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("clientInitials property tests")
class ClientInitialsFuzzTest {
    @Test
    fun `never throws, never exceeds two code points and never yields a lone surrogate for valid names`() {
        val seed = 0x1171001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val name = FuzzStrings.random(rnd, 12)
            val ctx = "seed=$seed iteration=$i name='${FuzzStrings.escape(name)}'"
            val result = runCatching { clientInitials(name) }
            assertNull(result.exceptionOrNull(), ctx)
            val initials = result.getOrThrow()
            val escaped = FuzzStrings.escape(initials)
            assertTrue(initials.codePointCount(0, initials.length) <= 2, "$ctx -> '$escaped' exceeds 2 characters")
            if (!FuzzStrings.hasLoneSurrogate(name)) {
                assertFalse(
                    FuzzStrings.hasLoneSurrogate(initials),
                    "$ctx -> '${FuzzStrings.escape(initials)}' has a lone surrogate",
                )
            }
            if (name.isBlank()) assertEquals("", initials, ctx)
        }
    }
}

private const val ITERATIONS = 5000
