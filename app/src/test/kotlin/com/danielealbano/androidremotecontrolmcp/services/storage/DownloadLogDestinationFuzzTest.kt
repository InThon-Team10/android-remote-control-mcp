package com.danielealbano.androidremotecontrolmcp.services.storage

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.random.Random

@DisplayName("downloadLogMessage destination property tests")
class DownloadLogDestinationFuzzTest {
    @Test
    fun `a destination containing a line break cannot forge a log line`() {
        val message = downloadLogMessage(5L, URL("https://host.example/f"), "builtin:downloads/a\nI/Forged: line")
        assertFalse(message.contains('\n'))
        assertEquals("Downloaded 5 bytes from https://host.example/f to builtin:downloads/aI/Forged: line", message)
    }

    @Test
    fun `the log line is free of control characters for any destination and keeps its printable content`() {
        val seed = 0xD057L
        val rnd = Random(seed)
        val url = URL("https://host.example:8443/p")
        repeat(ITERATIONS) { i ->
            val destination = FuzzStrings.random(rnd, 60)
            val ctx = "seed=$seed iteration=$i destination='${FuzzStrings.escape(destination)}'"
            val message = downloadLogMessage(rnd.nextLong(0, Long.MAX_VALUE), url, destination)
            assertTrue(message.none { it.isISOControl() }, "$ctx message='${FuzzStrings.escape(message)}'")
            assertTrue(message.endsWith(" to " + destination.filterNot { it.isISOControl() }), ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 5000
    }
}
