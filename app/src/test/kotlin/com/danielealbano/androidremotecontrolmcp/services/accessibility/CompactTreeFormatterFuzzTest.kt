package com.danielealbano.androidremotecontrolmcp.services.accessibility

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("CompactTreeFormatter property tests")
class CompactTreeFormatterFuzzTest {
    private val formatter = CompactTreeFormatter()
    private val screenInfo = ScreenInfo(width = 1080, height = 2400, densityDpi = 420, orientation = "portrait")

    private class Gen(
        val rnd: Random,
        val validOnly: Boolean,
    ) {
        var counter = 0

        fun text(maxLength: Int): String? =
            when (rnd.nextInt(4)) {
                0 -> null
                else -> if (validOnly) FuzzStrings.validUtf16(rnd, maxLength) else FuzzStrings.random(rnd, maxLength)
            }

        fun node(
            depth: Int,
            maxChildren: Int,
        ): AccessibilityNodeData {
            val childCount = if (depth >= MAX_DEPTH) 0 else rnd.nextInt(maxChildren + 1)
            return AccessibilityNodeData(
                id = "n${counter++}",
                className = text(TEXT_MAX),
                text = text(TEXT_MAX),
                contentDescription = text(TEXT_MAX),
                resourceId = text(TEXT_MAX),
                bounds = BoundsData(rnd.nextInt(), rnd.nextInt(), rnd.nextInt(), rnd.nextInt()),
                clickable = rnd.nextInt(4) == 0,
                longClickable = rnd.nextInt(5) == 0,
                focusable = rnd.nextBoolean(),
                scrollable = rnd.nextInt(5) == 0,
                editable = rnd.nextInt(6) == 0,
                enabled = rnd.nextBoolean(),
                visible = rnd.nextBoolean(),
                webRole = if (rnd.nextInt(8) == 0) "link" else null,
                children = List(childCount) { node(depth + 1, maxChildren) },
            )
        }

        fun window(
            id: Int,
            maxChildren: Int,
        ): WindowData =
            WindowData(
                windowId = id,
                windowType = "APPLICATION",
                packageName = text(HEADER_MAX),
                title = text(HEADER_MAX),
                activityName = text(HEADER_MAX),
                layer = rnd.nextInt(5),
                focused = rnd.nextBoolean(),
                tree = node(0, maxChildren),
            )
    }

    private class Parsed(
        val rowsPerWindow: List<List<String>>,
        val hierarchyPerWindow: List<List<String>>,
    )

    /** Re-parses formatted output the way a line oriented reader would (split on LF only). */
    private fun parse(output: String): Parsed {
        val rows = ArrayList<MutableList<String>>()
        val hierarchy = ArrayList<MutableList<String>>()
        var mode = 0
        for (line in output.split('\n')) {
            when {
                line.startsWith("--- window:") -> {
                    rows.add(ArrayList())
                    hierarchy.add(ArrayList())
                    mode = 0
                }

                line == CompactTreeFormatter.HEADER -> {
                    mode = 1
                }

                line == CompactTreeFormatter.HIERARCHY_HEADER -> {
                    mode = 2
                }

                line.startsWith("note:") || line.startsWith("page:") || line.startsWith("screen:") -> {
                    if (mode != 0 && line.startsWith("note:")) mode = 0
                }

                mode == 1 -> {
                    rows.last().add(line)
                }

                mode == 2 -> {
                    hierarchy.last().add(line.trimStart(' '))
                }
            }
        }
        return Parsed(rows, hierarchy)
    }

    private fun options(rnd: Random): ScreenStateFormatOptions {
        val offscreen = rnd.nextBoolean()
        return ScreenStateFormatOptions(includeOffscreen = offscreen, includeHierarchy = rnd.nextBoolean())
    }

    private fun preOrderListed(
        node: AccessibilityNodeData,
        options: ScreenStateFormatOptions,
        out: MutableList<String>,
    ) {
        if (formatter.isListed(node, options)) out.add(node.id)
        node.children.forEach { preOrderListed(it, options, out) }
    }

    @Test
    fun `formatted rows re-parse to the listed node count and no field can add rows`() {
        val seed = 0xC0A101L
        val rnd = Random(seed)
        repeat(TREE_ITERATIONS) { i ->
            val validOnly = rnd.nextBoolean()
            val gen = Gen(rnd, validOnly)
            val windows = List(rnd.nextInt(1, 4)) { gen.window(it + 1, 3) }
            val result = MultiWindowResult(windows, degraded = rnd.nextBoolean())
            val options = options(rnd)
            val ctx = "seed=$seed iteration=$i validOnly=$validOnly"
            val output = formatter.formatMultiWindow(result, screenInfo, options)
            assertFalse(output.contains('\r'), "$ctx output contains CR")
            val parsed = parse(output)
            assertEquals(windows.size, parsed.rowsPerWindow.size, "$ctx window sections")
            windows.forEachIndexed { w, window ->
                val expected = ArrayList<String>()
                preOrderListed(window.tree, options, expected)
                val rows = parsed.rowsPerWindow[w]
                assertEquals(expected, rows.map { it.substringBefore('\t') }, "$ctx window $w row ids")
                rows.forEach { row ->
                    assertEquals(
                        COLUMNS - 1,
                        row.count { it == '\t' },
                        "$ctx window $w row '${FuzzStrings.escape(row)}'",
                    )
                }
                if (options.includeHierarchy) assertEquals(expected, parsed.hierarchyPerWindow[w], "$ctx hierarchy $w")
            }
            if (validOnly) assertFalse(FuzzStrings.hasLoneSurrogate(output), "$ctx lone surrogate in output")
        }
    }

    @Test
    fun `window header stays on one line for arbitrary window strings`() {
        val seed = 0xC0A102L
        val rnd = Random(seed)
        repeat(TREE_ITERATIONS) { i ->
            val gen = Gen(rnd, false)
            val window = gen.window(1, 0)
            val header = formatter.buildWindowHeader(window)
            val ctx = "seed=$seed iteration=$i header='${FuzzStrings.escape(header)}'"
            assertTrue(header.none { it == '\n' || it == '\r' || it == '\t' }, ctx)
            assertTrue(header.startsWith("--- window:1 ") && header.endsWith(" ---"), ctx)
        }
    }

    @Test
    fun `pages partition the listed nodes in order, respect the page size and keep ancestors in the hierarchy`() {
        val seed = 0xC0A103L
        val rnd = Random(seed)
        repeat(PAGE_ITERATIONS) { i ->
            val gen = Gen(rnd, rnd.nextBoolean())
            val windows = List(rnd.nextInt(1, 3)) { gen.window(it + 1, rnd.nextInt(2, 9)) }
            val result = MultiWindowResult(windows, degraded = false)
            val options = options(rnd)
            val total = formatter.countKeptNodes(result, options)
            val pageSize = CompactTreeFormatter.PAGE_SIZE
            val totalPages = if (total <= 0) 1 else (total + pageSize - 1) / pageSize
            val snapshot = ScreenStateSnapshot("snap", result, screenInfo, total, totalPages, options)
            val ctx = "seed=$seed iteration=$i total=$total"
            val expectedAll = ArrayList<String>()
            windows.forEach { preOrderListed(it.tree, options, expectedAll) }
            val seen = ArrayList<String>()
            for (page in 1..totalPages) {
                val parsed = parse(formatter.formatMultiWindowPage(snapshot, page))
                val pageRows = parsed.rowsPerWindow.flatten().map { it.substringBefore('\t') }
                assertTrue(pageRows.size <= CompactTreeFormatter.PAGE_SIZE, "$ctx page $page has ${pageRows.size} rows")
                seen.addAll(pageRows)
                if (options.includeHierarchy) {
                    parsed.rowsPerWindow.indices.forEach { w ->
                        val hierarchy = parsed.hierarchyPerWindow[w].toSet()
                        val rows = parsed.rowsPerWindow[w].map { it.substringBefore('\t') }
                        assertTrue(hierarchy.containsAll(rows), "$ctx page $page window $w hierarchy misses rows")
                        assertTrue(
                            expectedAll.containsAll(hierarchy),
                            "$ctx page $page window $w hierarchy has unknown ids",
                        )
                    }
                }
            }
            assertEquals(expectedAll, seen, "$ctx rows across pages")
        }
    }

    @Test
    fun `sanitizeText output is single line, bounded, non-empty and never splits a surrogate pair`() {
        val seed = 0xC0A104L
        val rnd = Random(seed)
        repeat(TEXT_ITERATIONS) { i ->
            val validOnly = rnd.nextBoolean()
            val input = if (validOnly) FuzzStrings.validUtf16(rnd, LONG_TEXT) else FuzzStrings.random(rnd, LONG_TEXT)
            val truncate = rnd.nextBoolean()
            val out = formatter.sanitizeText(input, truncate)
            val ctx = "seed=$seed iteration=$i truncate=$truncate input='${FuzzStrings.escape(input)}'"
            assertTrue(out.none { it == '\t' || it == '\n' || it == '\r' }, ctx)
            assertTrue(out.isNotEmpty(), ctx)
            if (truncate) {
                assertTrue(
                    out.length <= CompactTreeFormatter.MAX_TEXT_LENGTH + CompactTreeFormatter.TRUNCATION_SUFFIX.length,
                    "$ctx length ${out.length}",
                )
            }
            if (validOnly) assertFalse(FuzzStrings.hasLoneSurrogate(out), "$ctx -> lone surrogate")
            val resourceId = formatter.sanitizeResourceId(input)
            assertTrue(resourceId.none { it == '\t' || it == '\n' || it == '\r' }, "$ctx resourceId")
            val simple = formatter.simplifyClassName(input)
            assertTrue(simple.none { it == '\t' || it == '\n' || it == '\r' }, "$ctx className")
        }
    }

    private companion object {
        const val TREE_ITERATIONS = 1500
        const val PAGE_ITERATIONS = 60
        const val TEXT_ITERATIONS = 6000
        const val LONG_TEXT = 300
        const val TEXT_MAX = 40
        const val HEADER_MAX = 30
        const val MAX_DEPTH = 4
        const val COLUMNS = 7
    }
}
