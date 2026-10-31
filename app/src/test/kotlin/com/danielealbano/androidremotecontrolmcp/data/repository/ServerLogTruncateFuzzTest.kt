package com.danielealbano.androidremotecontrolmcp.data.repository

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("ServerLogSegmentFiles.truncateUtf8 property tests")
class ServerLogTruncateFuzzTest {
    @Test
    fun `truncation is the longest whole-code-point prefix within the limit and decodes without replacement`() {
        val seed = 0x5106001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val input = FuzzStrings.validUtf16(rnd, 60)
            val limit = rnd.nextInt(0, 90)
            val ctx = "seed=$seed iteration=$i limit=$limit input='${FuzzStrings.escape(input)}'"
            val bytes = ServerLogSegmentFiles.truncateUtf8(input, limit)
            assertTrue(bytes.size <= limit, "$ctx size ${bytes.size}")
            val decoded = String(bytes, Charsets.UTF_8)
            val replacementChar = '\uFFFD'
            val introduced = decoded.contains(replacementChar) && !input.contains(replacementChar)
            assertFalse(introduced, "$ctx produced a replacement character")
            assertTrue(input.startsWith(decoded), "$ctx not a prefix")
            val full = input.toByteArray(Charsets.UTF_8)
            if (full.size <= limit) {
                assertEquals(input, decoded, ctx)
            } else {
                val next = input.offsetByCodePoints(decoded.length, 1)
                val longerPrefixSize = input.substring(0, next).toByteArray(Charsets.UTF_8).size
                assertTrue(longerPrefixSize > limit, "$ctx not the longest prefix")
            }
        }
    }

    private companion object {
        const val ITERATIONS = 6000
    }
}
