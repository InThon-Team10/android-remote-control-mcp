package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.ktor.http.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("normalizeBaseUrl and contentTypeOrOctetStream property tests")
class RequestBaseUrlFuzzTest {
    private fun label(rnd: Random): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return String(CharArray(rnd.nextInt(1, 10)) { alphabet[rnd.nextInt(alphabet.length)] })
    }

    private fun host(rnd: Random): String =
        if (rnd.nextInt(5) == 0) {
            "[" + List(rnd.nextInt(1, 8)) { rnd.nextInt(0, 0x10000).toString(16) }.joinToString(":") + "]"
        } else {
            List(rnd.nextInt(1, 5)) { label(rnd) }.joinToString(".")
        }

    @Test
    fun `well-formed base URLs normalize to lowercase scheme and host with default ports dropped, idempotently`() {
        val seed = 0xBA5E01L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val scheme = if (rnd.nextBoolean()) "http" else "https"
            val spelledScheme = if (rnd.nextBoolean()) scheme else scheme.uppercase()
            val host = host(rnd)
            val port = if (rnd.nextInt(3) == 0) null else listOf(80, 443, rnd.nextInt(1, 65536))[rnd.nextInt(3)]
            val portText = port?.let { ":" + "0".repeat(rnd.nextInt(0, 3)) + it } ?: ""
            val raw = "  $spelledScheme://$host$portText${if (rnd.nextBoolean()) "/" else ""} "
            val defaultPort = if (scheme == "https") 443 else 80
            val expected =
                if (port == null || port == defaultPort) {
                    "$scheme://${host.lowercase()}"
                } else {
                    "$scheme://${host.lowercase()}:$port"
                }
            val ctx = "seed=$seed iteration=$i raw='$raw'"
            val normalized = normalizeBaseUrl(raw)
            assertEquals(expected, normalized, ctx)
            assertEquals(normalized, normalizeBaseUrl(normalized), "$ctx idempotence")
            assertEquals("$normalized/mcp", canonicalResource(normalized), ctx)
        }
    }

    @Test
    fun `normalizeBaseUrl never throws on arbitrary text`() {
        val seed = 0xBA5E02L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val text = FuzzStrings.random(rnd, 40)
            val prefixed = if (rnd.nextBoolean()) "http://$text" else text
            assertNull(
                runCatching { normalizeBaseUrl(prefixed) }.exceptionOrNull(),
                "seed=$seed iteration=$i '${FuzzStrings.escape(prefixed)}'",
            )
        }
    }

    @Test
    fun `contentTypeOrOctetStream never throws and always yields a header-safe value`() {
        val seed = 0xBA5E03L
        val rnd = Random(seed)
        val known = listOf("text/plain", "image/png; charset=utf-8", "application/json", "text/html;a=b")
        repeat(ITERATIONS) { i ->
            val input =
                if (rnd.nextBoolean()) {
                    FuzzStrings.random(rnd, 30)
                } else {
                    val base = known[rnd.nextInt(known.size)]
                    val position = rnd.nextInt(base.length + 1)
                    base.substring(0, position) + FuzzStrings.random(rnd, 3) + base.substring(position)
                }
            val ctx = "seed=$seed iteration=$i input='${FuzzStrings.escape(input)}'"
            val result = runCatching { contentTypeOrOctetStream(input) }
            assertNull(result.exceptionOrNull(), ctx)
            val header = result.getOrThrow().toString()
            assertTrue(
                header.none { it.isISOControl() },
                "$ctx header='${FuzzStrings.escape(header)}' has control characters",
            )
            assertNull(runCatching { ContentType.parse(header) }.exceptionOrNull(), "$ctx re-parse")
        }
    }

    private companion object {
        const val ITERATIONS = 6000
    }
}
