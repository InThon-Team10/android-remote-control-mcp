package com.danielealbano.androidremotecontrolmcp.services.storage

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.MalformedURLException
import java.net.URL
import kotlin.random.Random

@DisplayName("sanitizeUrlForLog property tests")
class UrlLogSanitizerFuzzTest {
    private fun randomUrl(rnd: Random): URL? {
        val protocol = listOf("http", "https", "ftp", "file")[rnd.nextInt(4)]
        val host =
            if (rnd.nextInt(4) == 0) "" else FuzzStrings.random(rnd, 8).filter { it != '/' && it != ':' && it != '@' }
        val port = if (rnd.nextBoolean()) -1 else rnd.nextInt(0, 70000)
        val file = "/" + FuzzStrings.random(rnd, 30)
        val secrets = "?token=$QUERY_SECRET#$FRAGMENT_SECRET"
        return try {
            if (rnd.nextBoolean()) {
                URL(protocol, host, port, file + secrets)
            } else {
                val userInfo = if (rnd.nextBoolean()) "user:$USER_SECRET@" else ""
                val portText = if (port == -1) "" else ":$port"
                URL("$protocol://$userInfo$host$portText$file$secrets")
            }
        } catch (_: MalformedURLException) {
            null
        }
    }

    @Test
    fun `log form is single line free of control characters and never leaks userinfo query or fragment`() {
        val seed = 0x0A1001L
        val rnd = Random(seed)
        var built = 0
        repeat(ITERATIONS) { i ->
            val url = randomUrl(rnd) ?: return@repeat
            built++
            val ctx = "seed=$seed iteration=$i url='${FuzzStrings.escape(url.toString())}'"
            val out = sanitizeUrlForLog(url)
            assertTrue(
                out.none { it.isISOControl() },
                "$ctx output='${FuzzStrings.escape(out)}' has control characters",
            )
            assertFalse(
                listOf(QUERY_SECRET, FRAGMENT_SECRET, USER_SECRET).any { out.contains(it) },
                "$ctx leaked",
            )
            assertTrue(out.startsWith("${url.protocol}://"), ctx)
            val message = downloadLogMessage(1L, url, "location/file")
            assertTrue(message.contains(out), ctx)
        }
        assertTrue(built > ITERATIONS / 2, "only $built URLs could be built")
    }

    private companion object {
        const val ITERATIONS = 5000
        const val QUERY_SECRET = "QSECRETQ"
        const val FRAGMENT_SECRET = "FSECRETF"
        const val USER_SECRET = "USECRETU"
    }
}
