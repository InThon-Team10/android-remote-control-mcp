package com.danielealbano.androidremotecontrolmcp.data.model

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("BuiltinStorageLocation.validatePath property tests")
class StoragePathFuzzTest {
    private val pieces =
        listOf(
            ".",
            "..",
            "...",
            "a",
            "dir",
            "file.txt",
            "%2e%2e",
            "%2E%2E%2F",
            "..\\",
            "..\\..",
            "a\\b",
            "․․",
            "．．",
            "‮",
            "\u0000",
            "a\u0000b",
            "\u007F",
            "\u0085",
            "café",
            "a b",
            "~",
            "C:",
            "",
        )

    private fun randomPath(rnd: Random): String {
        val sb = StringBuilder()
        if (rnd.nextInt(6) == 0) sb.append('/')
        repeat(rnd.nextInt(0, 7)) {
            sb.append(
                when (rnd.nextInt(8)) {
                    0 -> FuzzStrings.random(rnd, 6).replace('/', '_')
                    1 -> "x".repeat(rnd.nextInt(1, 300))
                    else -> pieces[rnd.nextInt(pieces.size)]
                },
            )
            sb.append(if (rnd.nextInt(5) == 0) "//" else "/")
        }
        if (rnd.nextBoolean() && sb.isNotEmpty()) sb.setLength(sb.length - 1)
        return sb.toString()
    }

    private fun isAsciiControl(c: Char) = c.code < ASCII_SPACE || c.code == ASCII_DEL

    /** Resolves [path] the way a POSIX filesystem would; null when it would climb above the location root. */
    private fun resolveWithinRoot(path: String): List<String>? {
        val stack = ArrayList<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeAt(stack.size - 1)
                else -> stack.add(segment)
            }
        }
        return stack
    }

    @Test
    fun `validatePath rejects exactly absolute paths, dot segments and ASCII controls and never lets a path escape`() {
        val seed = 0x57A001L
        val rnd = Random(seed)
        var accepted = 0
        var rejected = 0
        repeat(ITERATIONS) { i ->
            val path = randomPath(rnd)
            val ctx = "seed=$seed iteration=$i path='${FuzzStrings.escape(path)}'"
            val expectedValid =
                !path.startsWith("/") &&
                    path.split("/").none { segment ->
                        segment == "." || segment == ".." || segment.any { isAsciiControl(it) }
                    }
            val outcome = runCatching { BuiltinStorageLocation.validatePath(path) }
            val failure = outcome.exceptionOrNull()
            if (failure != null) {
                assertTrue(failure is McpToolException.InvalidParams, "$ctx threw $failure")
                rejected++
            } else {
                accepted++
                val resolved = resolveWithinRoot(path)
                assertTrue(resolved != null, "$ctx escapes the location root")
                assertTrue(resolved!!.none { it == ".." || it == "." }, ctx)
            }
            assertEquals(expectedValid, failure == null, ctx)
        }
        assertTrue(accepted > 0 && rejected > 0, "accepted=$accepted rejected=$rejected")
    }

    @Test
    fun `very long paths and many segments are validated without error beyond InvalidParams`() {
        val seed = 0x57A002L
        val rnd = Random(seed)
        repeat(LONG_CASES) { i ->
            val segments =
                List(rnd.nextInt(1000, 20000)) { if (rnd.nextInt(2000) == 0) ".." else "s" + rnd.nextInt(10) }
            val path = segments.joinToString("/")
            val ctx = "seed=$seed iteration=$i length=${path.length}"
            val failure = runCatching { BuiltinStorageLocation.validatePath(path) }.exceptionOrNull()
            assertTrue(failure == null || failure is McpToolException.InvalidParams, "$ctx threw $failure")
            assertEquals(".." in segments, failure != null, ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 8000
        const val LONG_CASES = 20
        const val ASCII_SPACE = 0x20
        const val ASCII_DEL = 0x7F
    }
}
