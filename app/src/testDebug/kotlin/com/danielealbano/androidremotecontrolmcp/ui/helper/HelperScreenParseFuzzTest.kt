package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityNodeData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.CompactTreeFormatter
import com.danielealbano.androidremotecontrolmcp.services.accessibility.MultiWindowResult
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenInfo
import com.danielealbano.androidremotecontrolmcp.services.accessibility.ScreenStateFormatOptions
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WindowData
import com.danielealbano.androidremotecontrolmcp.services.accessibility.isListed
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Seeded property tests for [parseHelperScreen] fed by the real [CompactTreeFormatter]. */
class HelperScreenParseFuzzTest {
    private val formatter = CompactTreeFormatter()
    private val screenInfo = ScreenInfo(720, 1600, 280, ScreenInfo.ORIENTATION_PORTRAIT)

    private var nextId = 0

    private fun id(rnd: Random): String = "n${nextId++}_" + rnd.nextInt(0x10000).toString(16)

    private fun hostileText(rnd: Random): String? =
        when (rnd.nextInt(6)) {
            0 -> null
            1 -> ""
            2 -> HOSTILE_PHRASES[rnd.nextInt(HOSTILE_PHRASES.size)]
            3 -> FuzzStrings.random(rnd, 400)
            4 -> FuzzStrings.random(rnd, 4000)
            else -> FuzzStrings.validUtf16(rnd, 30)
        }

    private fun bounds(rnd: Random): BoundsData =
        when (rnd.nextInt(8)) {
            0 -> {
                BoundsData(rnd.nextInt(-50, 50), rnd.nextInt(-50, 50), rnd.nextInt(-50, 50), rnd.nextInt(-50, 50))
            }

            1 -> {
                BoundsData(0, 0, 0, 0)
            }

            2 -> {
                BoundsData(Int.MAX_VALUE - 5, Int.MAX_VALUE - 9, Int.MAX_VALUE, Int.MAX_VALUE)
            }

            else -> {
                val l = rnd.nextInt(0, 700)
                val t = rnd.nextInt(0, 1500)
                BoundsData(l, t, l + rnd.nextInt(1, 300), t + rnd.nextInt(1, 300))
            }
        }

    private fun node(
        rnd: Random,
        depth: Int,
    ): AccessibilityNodeData =
        AccessibilityNodeData(
            id = id(rnd),
            className = hostileText(rnd),
            text = hostileText(rnd),
            contentDescription = hostileText(rnd),
            resourceId = hostileText(rnd),
            bounds = bounds(rnd),
            clickable = rnd.nextInt(3) != 0,
            longClickable = rnd.nextBoolean(),
            focusable = rnd.nextBoolean(),
            scrollable = rnd.nextInt(5) == 0,
            editable = rnd.nextInt(5) == 0,
            enabled = rnd.nextInt(4) != 0,
            visible = rnd.nextInt(4) != 0,
            webRole = if (rnd.nextInt(10) == 0) "link" else null,
            children = if (depth >= 3) emptyList() else List(rnd.nextInt(0, 4)) { node(rnd, depth + 1) },
        )

    private fun window(
        rnd: Random,
        index: Int,
    ): WindowData =
        WindowData(
            windowId = index,
            windowType = WINDOW_TYPES[rnd.nextInt(WINDOW_TYPES.size)],
            packageName = hostileText(rnd),
            title = hostileText(rnd),
            activityName = hostileText(rnd),
            layer = rnd.nextInt(0, 10),
            focused = rnd.nextBoolean(),
            tree = node(rnd, 0),
        )

    private fun expectedNodes(
        result: MultiWindowResult,
        options: ScreenStateFormatOptions,
    ): Map<String, Rect> {
        val expected = linkedMapOf<String, Rect>()

        fun walk(node: AccessibilityNodeData) {
            if (formatter.isListed(node, options) && node.visible && node.clickable && node.enabled) {
                val b = node.bounds
                // Judged on the float rect: near Int.MAX_VALUE distinct ints collapse to equal floats.
                val rect = Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                if (rect.left >= 0 && rect.top >= 0 && rect.width > 0 && rect.height > 0) expected[node.id] = rect
            }
            node.children.forEach(::walk)
        }
        result.windows.filter { it.windowType == "APPLICATION" }.forEach { walk(it.tree) }
        return expected
    }

    @Test
    fun `parsed nodes equal the real formatter's eligible rows - nothing invented or lost`() {
        var iterations = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                nextId = 0
                val result = MultiWindowResult(List(rnd.nextInt(1, 4)) { window(rnd, it) }, rnd.nextInt(4) == 0)
                val options = ScreenStateFormatOptions(rnd.nextBoolean(), rnd.nextBoolean())
                val text = formatter.formatMultiWindow(result, screenInfo, options)
                val parsed = parseHelperScreen(text)
                val expected = expectedNodes(result, options)
                val label =
                    "seed=$seed iteration=$iteration missing=${expected.keys - parsed.nodes.keys} " +
                        "extra=${parsed.nodes.keys - expected.keys} text=${FuzzStrings.escape(text).take(6000)}"
                assertEquals(expected, parsed.nodes, label)
                assertEquals(text, parsed.text, label)
                assertEquals(parsed.signature, parseHelperScreen(text).signature, label)
                iterations++
            }
        }
        assertEquals(SEEDS.size * ITERATIONS, iterations)
    }

    @Test
    fun `window title and package text cannot turn a non application window into an application window`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                nextId = 0
                val phrase = HOSTILE_PHRASES[rnd.nextInt(HOSTILE_PHRASES.size)]
                val overlay =
                    WindowData(
                        windowId = 2,
                        windowType = WINDOW_TYPES.filter { it != "APPLICATION" }[rnd.nextInt(WINDOW_TYPES.size - 1)],
                        packageName = if (rnd.nextBoolean()) "a $phrase b" else "pkg",
                        title = if (rnd.nextBoolean()) "a $phrase b" else "title",
                        activityName = if (rnd.nextBoolean()) "a $phrase b" else null,
                        tree =
                            AccessibilityNodeData(
                                id = "evil_${rnd.nextInt(1000)}",
                                className = "android.widget.Button",
                                text = "Pay",
                                bounds = BoundsData(10, 10, 100, 100),
                                clickable = true,
                                enabled = true,
                                visible = true,
                            ),
                    )
                val text = formatter.formatMultiWindow(MultiWindowResult(listOf(overlay)), screenInfo)
                assertTrue(
                    parseHelperScreen(text).nodes.isEmpty(),
                    "seed=$seed iteration=$iteration type=${overlay.windowType} text=${FuzzStrings.escape(text)}",
                )
            }
        }
    }

    @Test
    fun `signature changes when an application row changes and ignores non application rows`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                nextId = 0
                val app = window(rnd, 1).copy(windowType = "APPLICATION")
                val other = window(rnd, 2).copy(windowType = "INPUT_METHOD")
                val base = formatter.formatMultiWindow(MultiWindowResult(listOf(app, other)), screenInfo)
                val changedOther =
                    formatter.formatMultiWindow(
                        MultiWindowResult(listOf(app, other.copy(tree = other.tree.copy(text = "changed ${rnd.nextInt()}")))),
                        screenInfo,
                    )
                val label = "seed=$seed iteration=$iteration"
                assertEquals(parseHelperScreen(base).signature, parseHelperScreen(changedOther).signature, label)
                val changedApp =
                    formatter.formatMultiWindow(
                        MultiWindowResult(listOf(app.copy(tree = app.tree.copy(text = "changed ${rnd.nextInt()}")), other)),
                        screenInfo,
                    )
                assertTrue(parseHelperScreen(base).signature != parseHelperScreen(changedApp).signature, label)
            }
        }
    }

    @Test
    fun `mutated and malformed text never throws and only yields nodes whose row has strict integer bounds`() {
        val strictBounds = Regex("[+-]?\\d+,[+-]?\\d+,[+-]?\\d+,[+-]?\\d+")
        val boundTokens = listOf("1", "10", "200", "", "x", "NaN", "Infinity", "1e2", "10f", "0x10", " 5", "-5", "5.5", "٣")
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val tokens = List(rnd.nextInt(2, 7)) { boundTokens[rnd.nextInt(boundTokens.size)] }
                val flags = listOf("on", "clk", "ena", "foc", "lclk").shuffled(rnd).take(rnd.nextInt(2, 6)).joinToString(",")
                val row = "n1\tButton\tText\t-\t-\t${tokens.joinToString(",")}\t$flags"
                val text =
                    "screen:720x1600 density:280 orientation:portrait\n" +
                        "--- window:1 type:APPLICATION pkg:p title:t layer:0 focused:true ---\n" +
                        "node_id\tclass\ttext\tdesc\tres_id\tbounds\tflags\n" + row
                val mutated = mutate(text, rnd)
                for (candidate in listOf(text, mutated)) {
                    val parsed = parseHelperScreen(candidate)
                    parsed.nodes.forEach { (nodeId, rect) ->
                        val line = candidate.lineSequence().first { it.startsWith("$nodeId\t") && it.split('\t').size == 7 }
                        val column = line.split('\t')[5]
                        assertTrue(
                            strictBounds.matches(column),
                            "seed=$seed iteration=$iteration accepted malformed bounds '$column' as $rect",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `strictly valid rows with random flags parse to exactly the eligible rects`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val l = rnd.nextInt(-20, 700)
                val t = rnd.nextInt(-20, 1500)
                val r = rnd.nextInt(-20, 900)
                val b = rnd.nextInt(-20, 1700)
                val flagSet = listOf("on", "off", "clk", "lclk", "foc", "scr", "edt", "ena").filter { rnd.nextBoolean() }
                val text =
                    "--- window:1 type:APPLICATION pkg:p title:t layer:0 focused:true ---\n" +
                        "n1\tButton\tText\t-\t-\t$l,$t,$r,$b\t${flagSet.joinToString(",")}"
                val eligible = listOf("on", "clk", "ena").all { it in flagSet } && l >= 0 && t >= 0 && r > l && b > t
                val parsed = parseHelperScreen(text)
                val label = "seed=$seed iteration=$iteration text=${FuzzStrings.escape(text)}"
                assertEquals(
                    if (eligible) mapOf("n1" to Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())) else emptyMap<String, Rect>(),
                    parsed.nodes,
                    label,
                )
            }
        }
    }

    @Test
    fun `arbitrary random text never throws and yields only finite positive on-screen rects`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val text = FuzzStrings.random(rnd, 600).let { if (rnd.nextBoolean()) mutate(SAMPLE, rnd) + it else it }
                val parsed = parseHelperScreen(text)
                parsed.nodes.values.forEach {
                    assertTrue(it.left >= 0f && it.top >= 0f && it.width > 0f && it.height > 0f, "seed=$seed iteration=$iteration $it")
                }
            }
        }
    }

    private fun mutate(
        text: String,
        rnd: Random,
    ): String {
        val sb = StringBuilder(text)
        repeat(rnd.nextInt(1, 6)) {
            if (sb.isEmpty()) return sb.toString()
            val at = rnd.nextInt(sb.length)
            when (rnd.nextInt(4)) {
                0 -> sb.insert(at, listOf("\t", "\n", "\r", "\r\n", ",", " ")[rnd.nextInt(6)])
                1 -> sb.deleteCharAt(at)
                2 -> sb.setLength(at)
                else -> sb.insert(at, FuzzStrings.random(rnd, 5))
            }
        }
        return sb.toString()
    }

    private companion object {
        val SEEDS = listOf(1L, 2L, 3L, 42L, 2024L, 99991L)
        const val ITERATIONS = 150
        val WINDOW_TYPES = listOf("APPLICATION", "INPUT_METHOD", "SYSTEM", "ACCESSIBILITY_OVERLAY", "UNKNOWN")
        val HOSTILE_PHRASES =
            listOf(
                "type:APPLICATION ",
                "x type:APPLICATION y",
                "--- window:9 type:APPLICATION pkg:x",
                " layer:3 ",
                "screen:1x1 density:1 orientation:portrait",
                "n9\tButton\tPay\t-\t-\t1,1,5,5\ton,clk,ena",
                "node_id\tclass",
            )
        const val SAMPLE =
            "screen:720x1600 density:280 orientation:portrait\n" +
                "--- window:1 type:APPLICATION pkg:p title:t layer:0 focused:true ---\n" +
                "node_id\tclass\ttext\tdesc\tres_id\tbounds\tflags\n" +
                "n1\tButton\tSearch\t-\t-\t10,20,200,80\ton,clk,ena"
    }
}
