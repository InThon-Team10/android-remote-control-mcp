package com.danielealbano.androidremotecontrolmcp.services.accessibility

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("ElementFinder property tests")
class ElementFinderFuzzTest {
    private val finder = ElementFinder()

    private fun randomText(rnd: Random): String? =
        when (rnd.nextInt(6)) {
            0 -> null
            1 -> ""
            2 -> listOf("Button", "button", "BUTTON", "OK", "ok", "Ok")[rnd.nextInt(6)]
            else -> FuzzStrings.random(rnd, 10)
        }

    private fun randomTree(
        rnd: Random,
        depth: Int,
    ): AccessibilityNodeData {
        val children =
            if (depth <= 0) {
                emptyList()
            } else {
                List(rnd.nextInt(0, 4)) { randomTree(rnd, depth - 1) }
            }
        return AccessibilityNodeData(
            id = "n" + rnd.nextInt(40),
            className = randomText(rnd),
            text = randomText(rnd),
            contentDescription = randomText(rnd),
            resourceId = randomText(rnd),
            bounds = BoundsData(0, 0, 1, 1),
            children = children,
        )
    }

    private fun preorder(node: AccessibilityNodeData): List<AccessibilityNodeData> =
        buildList {
            add(node)
            node.children.forEach { addAll(preorder(it)) }
        }

    private fun property(
        node: AccessibilityNodeData,
        by: FindBy,
    ): String? =
        when (by) {
            FindBy.TEXT -> node.text
            FindBy.CONTENT_DESC -> node.contentDescription
            FindBy.RESOURCE_ID -> node.resourceId
            FindBy.CLASS_NAME -> node.className
        }

    private fun property(
        info: ElementInfo,
        by: FindBy,
    ): String? =
        when (by) {
            FindBy.TEXT -> info.text
            FindBy.CONTENT_DESC -> info.contentDescription
            FindBy.RESOURCE_ID -> info.resourceId
            FindBy.CLASS_NAME -> info.className
        }

    // Independent of String.contains(ignoreCase): char-wise comparison by upper then lower case folding
    private fun referenceContainsIgnoreCase(
        haystack: String,
        needle: String,
    ): Boolean {
        fun same(
            a: Char,
            b: Char,
        ) = a == b || a.uppercaseChar() == b.uppercaseChar() || a.lowercaseChar() == b.lowercaseChar()
        return needle.length <= haystack.length &&
            (0..haystack.length - needle.length).any { start ->
                needle.indices.all { same(haystack[start + it], needle[it]) }
            }
    }

    @Test
    fun `findElements returns exactly the preorder nodes matching contains or equals`() {
        val seed = 0xE1F001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val tree = randomTree(rnd, rnd.nextInt(0, 5))
            val nodes = preorder(tree)
            val by = FindBy.entries[rnd.nextInt(FindBy.entries.size)]
            val exact = rnd.nextBoolean()
            val value =
                if (rnd.nextBoolean()) {
                    randomText(rnd).orEmpty()
                } else {
                    val source = nodes.mapNotNull { property(it, by) }.randomOrNull(rnd).orEmpty()
                    val from = if (source.isEmpty()) 0 else rnd.nextInt(source.length)
                    val to = if (source.isEmpty()) 0 else rnd.nextInt(from, source.length + 1)
                    if (exact) source else source.substring(from, to)
                }
            val ctx = "seed=$seed iteration=$i by=$by exact=$exact value='${FuzzStrings.escape(value)}'"
            val expected =
                nodes.filter { node ->
                    val candidate = property(node, by) ?: return@filter false
                    if (exact) candidate == value else referenceContainsIgnoreCase(candidate, value)
                }
            val actual = finder.findElements(tree, by, value, exact)
            assertEquals(expected.map { it.id }, actual.map { it.id }, ctx)
            assertEquals(expected.map { property(it, by) }, actual.map { property(it, by) }, ctx)
            val windows = listOf(WindowData(1, "APPLICATION", tree = tree), WindowData(2, "SYSTEM", tree = tree))
            assertEquals(actual + actual, finder.findElements(windows, by, value, exact), "$ctx windows")
        }
    }

    @Test
    fun `findNodeById returns the first preorder node with that id or null`() {
        val seed = 0xE1F002L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val tree = randomTree(rnd, rnd.nextInt(0, 5))
            val nodes = preorder(tree)
            val id = "n" + rnd.nextInt(45)
            val ctx = "seed=$seed iteration=$i id=$id"
            val expected = nodes.firstOrNull { it.id == id }
            val actual = finder.findNodeById(tree, id)
            if (expected == null) assertNull(actual, ctx) else assertSame(expected, actual, ctx)
        }
    }

    @Test
    fun `huge patterns and wide deep trees stay linear and never throw`() {
        val seed = 0xE1F003L
        val rnd = Random(seed)
        repeat(HUGE_CASES) { i ->
            val chain =
                (0 until MAX_DEPTH).fold(null as AccessibilityNodeData?) { child, depth ->
                    AccessibilityNodeData(
                        id = "d$depth",
                        text = "a".repeat(HUGE_TEXT),
                        bounds = BoundsData(0, 0, 1, 1),
                        children = listOfNotNull(child),
                    )
                }!!
            val wide =
                AccessibilityNodeData(
                    id = "root",
                    bounds = BoundsData(0, 0, 1, 1),
                    children =
                        List(WIDE) { AccessibilityNodeData("w$it", text = "x$it", bounds = BoundsData(0, 0, 1, 1)) },
                )
            val pattern = "a".repeat(rnd.nextInt(HUGE_TEXT, HUGE_TEXT * 2)) + "b"
            val started = System.nanoTime()
            assertTrue(finder.findElements(chain, FindBy.TEXT, pattern, false).isEmpty(), "seed=$seed iteration=$i")
            assertTrue(finder.findElements(chain, FindBy.TEXT, pattern, true).isEmpty(), "seed=$seed iteration=$i")
            assertEquals(WIDE, finder.findElements(wide, FindBy.TEXT, "X", false).size, "seed=$seed iteration=$i")
            val elapsedMs = (System.nanoTime() - started) / NANOS_PER_MS
            assertTrue(elapsedMs < TIME_GUARD_MS, "seed=$seed iteration=$i took ${elapsedMs}ms")
        }
    }

    private companion object {
        const val ITERATIONS = 4000
        const val HUGE_CASES = 3
        const val MAX_DEPTH = 100
        const val HUGE_TEXT = 2_000
        const val WIDE = 20_000
        const val NANOS_PER_MS = 1_000_000L
        const val TIME_GUARD_MS = 20_000L
    }
}
