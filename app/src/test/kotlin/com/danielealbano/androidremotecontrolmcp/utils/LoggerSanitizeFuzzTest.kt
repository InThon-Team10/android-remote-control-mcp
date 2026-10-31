package com.danielealbano.androidremotecontrolmcp.utils

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

@DisplayName("Logger.sanitize property tests")
class LoggerSanitizeFuzzTest {
    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    @Test
    fun `every embedded token is masked, nothing else changes and the result is stable`() {
        val seed = 0x106601L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val raw = UUID(rnd.nextLong(), rnd.nextLong()).toString()
            val token = if (rnd.nextBoolean()) raw.uppercase() else raw
            val prefix = FuzzStrings.random(rnd, 30).replace('-', '_')
            val suffix = FuzzStrings.random(rnd, 30).replace('-', '_')
            val message = "$prefix$token$suffix"
            val ctx = "seed=$seed iteration=$i message='${FuzzStrings.escape(message)}'"
            val sanitized = Logger.sanitize(message)
            assertFalse(uuidPattern.containsMatchIn(sanitized), "$ctx still holds a token")
            assertFalse(sanitized.contains(token), ctx)
            assertEquals(sanitized, Logger.sanitize(sanitized), "$ctx idempotence")
            val clean = prefix + suffix
            if (!uuidPattern.containsMatchIn(clean)) {
                assertEquals(clean, Logger.sanitize(clean), "$ctx altered clean text")
            }
        }
    }

    @Test
    fun `arbitrary text never throws and never keeps a token`() {
        val seed = 0x106602L
        val rnd = Random(seed)
        val hex = "0123456789abcdefABCDEF-"
        repeat(ITERATIONS) { i ->
            val message = String(CharArray(rnd.nextInt(0, 80)) { hex[rnd.nextInt(hex.length)] })
            val sanitized = Logger.sanitize(message)
            assertFalse(uuidPattern.containsMatchIn(sanitized), "seed=$seed iteration=$i message='$message'")
        }
    }

    private companion object {
        const val ITERATIONS = 5000
    }
}
