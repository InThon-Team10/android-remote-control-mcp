package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URL
import kotlin.random.Random

@DisplayName("SettingsRepositoryImpl pure validators property tests")
class SettingsValidatorsFuzzTest {
    private val repository: SettingsRepository =
        SettingsRepositoryImpl(
            mockk<DataStore<Preferences>>(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
            mockk(relaxed = true),
        )

    private fun trailer(rnd: Random): String = listOf("", ".", "\n", " ")[rnd.nextInt(4)]

    private fun <T> guarded(
        ctx: String,
        block: () -> T,
    ): T = runCatching(block).getOrElse { throw AssertionError(ctx, it) }

    private fun label(rnd: Random): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-"
        return String(CharArray(rnd.nextInt(0, 70)) { alphabet[rnd.nextInt(alphabet.length)] })
    }

    // Independent of the production regex: split on dots, check each label character by character
    private fun referenceIsHostname(text: String): Boolean {
        if (text.length !in 1..MAX_HOSTNAME) return false
        return text.split('.').all { label ->
            label.length in 1..MAX_LABEL &&
                label.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' } &&
                label.first() != '-' &&
                label.last() != '-'
        }
    }

    @Test
    fun `hostname validation matches a character-level reference`() {
        val seed = 0x5E7001L
        val rnd = Random(seed)
        var accepted = 0
        repeat(ITERATIONS) { i ->
            val hostname =
                when (rnd.nextInt(4)) {
                    0 -> FuzzStrings.random(rnd, 30)
                    1 -> List(rnd.nextInt(1, 5)) { label(rnd) }.joinToString(".")
                    2 -> List(rnd.nextInt(1, 5)) { label(rnd).take(10) }.joinToString(".") + trailer(rnd)
                    else -> List(rnd.nextInt(1, 60)) { "ab" }.joinToString(".")
                }
            val ctx = "seed=$seed iteration=$i hostname='${FuzzStrings.escape(hostname)}' length=${hostname.length}"
            val result = guarded(ctx) { repository.validateCertificateHostname(hostname) }
            assertEquals(hostname.isNotBlank() && referenceIsHostname(hostname), result.isSuccess, ctx)
            if (result.isSuccess) {
                assertEquals(hostname, result.getOrNull(), ctx)
                accepted++
            }
        }
        assertTrue(accepted > 0, "no hostname accepted")
    }

    @Test
    fun `device slug and port validation match their references`() {
        val seed = 0x5E7002L
        val rnd = Random(seed)
        val slugChars = "abcXYZ019_"
        repeat(ITERATIONS) { i ->
            val slug =
                if (rnd.nextBoolean()) {
                    FuzzStrings.random(rnd, 25)
                } else {
                    String(CharArray(rnd.nextInt(0, 26)) { slugChars[rnd.nextInt(slugChars.length)] })
                }
            val ctx = "seed=$seed iteration=$i slug='${FuzzStrings.escape(slug)}'"
            val expected =
                slug.length <= ServerConfig.MAX_DEVICE_SLUG_LENGTH &&
                    slug.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
            assertEquals(expected, repository.validateDeviceSlug(slug).isSuccess, ctx)
            val port = rnd.nextInt(-70000, 140000)
            assertEquals(port in 1..65535, repository.validatePort(port).isSuccess, "$ctx port=$port")
        }
    }

    private fun randomUrl(rnd: Random): String {
        val scheme = listOf("http", "https", "HTTPS", "ftp", "file", "", "javascript")[rnd.nextInt(7)]
        val userInfo = if (rnd.nextInt(5) == 0) "user:pw@" else ""
        val host =
            when (rnd.nextInt(5)) {
                0 -> ""
                1 -> "[" + List(rnd.nextInt(1, 8)) { rnd.nextInt(0x10000).toString(16) }.joinToString(":") + "]"
                2 -> FuzzStrings.random(rnd, 10)
                else -> List(rnd.nextInt(1, 4)) { label(rnd).take(8) }.joinToString(".")
            }
        val port = if (rnd.nextInt(3) == 0) ":" + PORT_TEXTS[rnd.nextInt(PORT_TEXTS.size)] else ""
        val path = if (rnd.nextBoolean()) "/" + FuzzStrings.random(rnd, 12) else ""
        val tail = listOf("", "", "", "?q=1", "#frag", "?", "#")[rnd.nextInt(7)]
        val padding = if (rnd.nextInt(6) == 0) listOf(" ", "\n", "\t", " ")[rnd.nextInt(4)] else ""
        return "$padding$scheme://$userInfo$host$port$path$tail$padding"
    }

    @Test
    fun `accepted public URL overrides are well-formed http or https URLs without embedded control characters`() {
        val seed = 0x5E7003L
        val rnd = Random(seed)
        var accepted = 0
        repeat(ITERATIONS) { i ->
            val raw = randomUrl(rnd)
            val ctx = "seed=$seed iteration=$i url='${FuzzStrings.escape(raw)}'"
            val result = guarded(ctx) { repository.validatePublicUrlOverride(raw) }
            if (raw.isBlank()) {
                assertEquals("", result.getOrNull(), ctx)
                return@repeat
            }
            val value = result.getOrNull() ?: return@repeat
            accepted++
            assertEquals(raw, value, ctx)
            assertTrue(value.trim().none { it.isISOControl() }, "$ctx accepted an embedded control character")
            val url = URL(value)
            assertTrue(url.protocol == "http" || url.protocol == "https", ctx)
            assertTrue(!url.host.isNullOrEmpty(), ctx)
            assertTrue(url.port <= ServerConfig.MAX_PORT, ctx)
            assertTrue(url.query == null && url.ref == null, ctx)
        }
        assertTrue(accepted > 0, "no URL accepted")
    }

    private companion object {
        const val ITERATIONS = 6000
        const val MAX_HOSTNAME = 253
        const val MAX_LABEL = 63
        val PORT_TEXTS = listOf("80", "65535", "65536", "99999", "-1", "0", "x", "")
    }
}
