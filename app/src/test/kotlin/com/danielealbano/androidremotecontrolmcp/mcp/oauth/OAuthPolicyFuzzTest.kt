package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.Base64
import kotlin.random.Random

@DisplayName("OAuthPolicy and Pkce property tests")
class OAuthPolicyFuzzTest {
    private val bases =
        listOf(
            OAuthPolicy.CLAUDE_REDIRECT_URI,
            OAuthPolicy.CHATGPT_REDIRECT_URI,
            "https://chatgpt.com/connector/oauth/abc123",
            "http://localhost:3000/callback",
            "http://127.0.0.1/cb",
            "http://[::1]:8080/cb",
        )

    private val fragments =
        listOf(
            "@evil.com",
            ".evil.com",
            "evil.com",
            "%2e%2e/",
            "%2E",
            "/../",
            "/./",
            "\\",
            "#@evil.com",
            "?@evil.com",
            "%00",
            ":80@",
            "//",
            " ",
            "\t",
            "..;/",
            "\u0000",
            "\u2028",
            "https://",
            "http://",
            "chatgpt.com",
            "localhost",
            "0.0.0.0",
            "[::1]",
            "%40",
            "%5c",
        )

    private fun mutate(rnd: Random): String {
        if (rnd.nextInt(6) == 0) return FuzzStrings.random(rnd, 40)
        val sb = StringBuilder(bases[rnd.nextInt(bases.size)])
        repeat(rnd.nextInt(1, 4)) {
            val piece = fragments[rnd.nextInt(fragments.size)]
            when (rnd.nextInt(4)) {
                0 -> sb.insert(rnd.nextInt(sb.length + 1), piece)
                1 -> sb.append(piece)
                2 -> if (sb.isNotEmpty()) sb.deleteCharAt(rnd.nextInt(sb.length))
                else -> if (sb.isNotEmpty()) sb.setCharAt(rnd.nextInt(sb.length), piece.first())
            }
        }
        return sb.toString()
    }

    private class Split(
        val scheme: String,
        val host: String,
        val path: String,
    )

    /** WHATWG-style split: backslash acts as slash, userinfo is everything up to the last '@' in the authority. */
    private fun split(uri: String): Split? {
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd < 0) return null
        val rest = uri.substring(schemeEnd + 3)
        val isDelimiter = { c: Char -> c == '/' || c == '?' || c == '#' || c == '\\' }
        val authorityEnd = rest.indexOfFirst(isDelimiter).let { if (it < 0) rest.length else it }
        val hostPort = rest.substring(0, authorityEnd).substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']') + "]" else hostPort.substringBefore(':')
        val rawPath =
            rest
                .substring(authorityEnd)
                .substringBefore('?')
                .substringBefore('#')
                .replace('\\', '/')
        val segments = ArrayList<String>()
        for (segment in rawPath.split('/').drop(1)) {
            val lowered = segment.lowercase().replace("%2e", ".")
            when (lowered) {
                "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(segment)
            }
        }
        return Split(uri.substring(0, schemeEnd).lowercase(), host.lowercase(), "/" + segments.joinToString("/"))
    }

    @Test
    fun `isAllowedRedirectUri never throws and only allows the closed host set`() {
        val seed = 0x0A07001L
        val rnd = Random(seed)
        var allowed = 0
        repeat(ITERATIONS) { i ->
            val uri = mutate(rnd)
            val ctx = "seed=$seed iteration=$i uri='${FuzzStrings.escape(uri)}'"
            val result = runCatching { OAuthPolicy.isAllowedRedirectUri(uri) }
            assertNull(result.exceptionOrNull(), ctx)
            if (!result.getOrThrow() || uri in OAuthPolicy.ALLOWED_REDIRECT_URIS) return@repeat
            allowed++
            val parts = split(uri)
            assertTrue(parts != null, "$ctx allowed without a scheme separator")
            val loopback = parts!!.scheme == "http" && parts.host in setOf("localhost", "127.0.0.1", "[::1]")
            val chatGpt =
                parts.scheme == "https" && parts.host == "chatgpt.com" && parts.path.startsWith("/connector/oauth/")
            assertTrue(loopback || chatGpt, "$ctx allowed but resolves to ${parts.scheme}://${parts.host}${parts.path}")
        }
        assertTrue(allowed > 100, "only $allowed non-exact URIs were allowed, the generator is too destructive")
    }

    @Test
    fun `verifyS256 accepts exactly the matching challenge`() {
        val seed = 0x0A07002L
        val rnd = Random(seed)
        val unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        repeat(ITERATIONS) { i ->
            val verifier = String(CharArray(rnd.nextInt(43, 129)) { unreserved[rnd.nextInt(unreserved.length)] })
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            val ctx = "seed=$seed iteration=$i verifier=$verifier"
            assertTrue(Pkce.verifyS256(verifier, challenge), ctx)
            val position = rnd.nextInt(verifier.length)
            val other = unreserved.first { it != verifier[position] }
            val tampered = verifier.replaceRange(position, position + 1, other.toString())
            assertEquals(false, Pkce.verifyS256(tampered, challenge), "$ctx tampered")
            assertEquals(false, Pkce.verifyS256(verifier, challenge.dropLast(1)), "$ctx truncated challenge")
            assertEquals(false, Pkce.verifyS256(verifier, "$challenge="), "$ctx padded challenge")
            val garbage = FuzzStrings.random(rnd, 60)
            assertNull(runCatching { Pkce.verifyS256(garbage, garbage) }.exceptionOrNull(), "$ctx garbage")
        }
    }

    private companion object {
        const val ITERATIONS = 6000
    }
}
