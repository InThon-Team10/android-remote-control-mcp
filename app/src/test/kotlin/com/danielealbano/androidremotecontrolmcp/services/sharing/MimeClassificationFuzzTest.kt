package com.danielealbano.androidremotecontrolmcp.services.sharing

import com.danielealbano.androidremotecontrolmcp.services.storage.MimeTypeUtils
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("SharedContentClassifier and MimeTypeUtils property tests")
class MimeClassificationFuzzTest {
    private val allowlist =
        listOf(
            "application/json",
            "application/xml",
            "application/xhtml+xml",
            "application/javascript",
            "application/ld+json",
            "application/yaml",
            "text/yaml",
        )

    private fun spaces(rnd: Random): String = " ".repeat(rnd.nextInt(0, 3)) + "\t".repeat(rnd.nextInt(0, 2))

    private fun randomCase(
        rnd: Random,
        text: String,
    ): String = text.map { if (rnd.nextBoolean()) it.uppercaseChar() else it.lowercaseChar() }.joinToString("")

    @Test
    fun `isTextual accepts text subtypes and the allowlist in any case with parameters and padding`() {
        val seed = 0x31A001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val base =
                if (rnd.nextBoolean()) {
                    "text/" + FuzzStrings.random(rnd, 10).filter { it != ';' }
                } else {
                    allowlist[rnd.nextInt(allowlist.size)]
                }
            val parameters = if (rnd.nextBoolean()) ";" + FuzzStrings.random(rnd, 20) else ""
            val input = spaces(rnd) + randomCase(rnd, base) + spaces(rnd) + parameters
            val ctx = "seed=$seed iteration=$i input='${FuzzStrings.escape(input)}'"
            assertTrue(SharedContentClassifier.isTextual(input), ctx)
        }
    }

    @Test
    fun `isTextual rejects everything outside text subtypes and the allowlist and never throws`() {
        val seed = 0x31A002L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val input = FuzzStrings.random(rnd, 30)
            val mime = input.substringBefore(';').trim().lowercase()
            val expected = mime.startsWith("text/") || mime in allowlist
            val ctx = "seed=$seed iteration=$i input='${FuzzStrings.escape(input)}'"
            val outcome = runCatching { SharedContentClassifier.isTextual(input) }
            assertNull(outcome.exceptionOrNull(), ctx)
            assertEquals(expected, outcome.getOrNull(), ctx)
            if (expected) {
                assertTrue("text/" in mime || mime in allowlist, ctx)
            }
            assertFalse(SharedContentClassifier.isTextual("image/" + input.filter { it != ';' }), ctx)
        }
    }

    @Test
    fun `isImage matches the image slash prefix case insensitively and is exclusive with isTextual`() {
        val seed = 0x31A003L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val suffix = FuzzStrings.random(rnd, 15)
            val image = spaces(rnd) + randomCase(rnd, "image/") + suffix
            val other = FuzzStrings.random(rnd, 25)
            val ctx = "seed=$seed iteration=$i suffix='${FuzzStrings.escape(suffix)}'"
            assertTrue(SharedContentClassifier.isImage(image), ctx)
            assertFalse(SharedContentClassifier.isTextual(image), ctx)
            val trimmedLower = other.trim().lowercase()
            assertEquals(trimmedLower.startsWith("image/"), SharedContentClassifier.isImage(other), "$ctx other")
        }
    }

    @Test
    fun `guessMimeType maps by the last extension case insensitively and defaults to octet-stream`() {
        val seed = 0x31A004L
        val rnd = Random(seed)
        val known =
            mapOf(
                "txt" to "text/plain",
                "json" to "application/json",
                "png" to "image/png",
                "jpg" to "image/jpeg",
                "jpeg" to "image/jpeg",
                "yml" to "text/yaml",
                "apk" to "application/vnd.android.package-archive",
            )
        repeat(ITERATIONS) { i ->
            val stem = FuzzStrings.random(rnd, 12)
            val extension = known.keys.toList()[rnd.nextInt(known.size)]
            val name = stem + "." + randomCase(rnd, extension)
            val ctx = "seed=$seed iteration=$i name='${FuzzStrings.escape(name)}'"
            assertEquals(known[extension], MimeTypeUtils.guessMimeType(name), ctx)
            val arbitrary = FuzzStrings.random(rnd, 20)
            val result = MimeTypeUtils.guessMimeType(arbitrary)
            val lowered = "f." + arbitrary.substringAfterLast('.', "").lowercase()
            val arbitraryCtx = "$ctx arbitrary='${FuzzStrings.escape(arbitrary)}'"
            assertEquals(MimeTypeUtils.guessMimeType(lowered), result, arbitraryCtx)
            assertEquals("application/octet-stream", MimeTypeUtils.guessMimeType(stem.replace(".", "")), ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 5000
    }
}
