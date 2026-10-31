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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

/** Seeded property tests for the swipe guide: [swipePath], scroll node parsing and swipe reply decoding. */
class HelperSwipeFuzzTest {
    private val formatter = CompactTreeFormatter()
    private val screenInfo = ScreenInfo(720, 1600, 280, ScreenInfo.ORIENTATION_PORTRAIT)

    private fun anyFloat(rnd: Random): Float =
        when (rnd.nextInt(12)) {
            0 -> Float.NaN
            1 -> Float.POSITIVE_INFINITY
            2 -> Float.NEGATIVE_INFINITY
            3 -> -0f
            4 -> Float.MAX_VALUE
            5 -> -Float.MAX_VALUE
            6 -> Float.MIN_VALUE
            else -> rnd.nextInt(-300, 2000) / (if (rnd.nextBoolean()) 1f else 7f)
        }

    private fun boundedFloat(rnd: Random): Float =
        anyFloat(rnd).let {
            if (it.isFinite() &&
                abs(it) < 1e9f
            ) {
                it
            } else {
                rnd.nextInt(-3000, 3000).toFloat()
            }
        }

    private fun saneRect(
        rnd: Random,
        maxX: Int,
        maxY: Int,
    ): Rect {
        val l = rnd.nextInt(-200, maxX + 200).toFloat()
        val t = rnd.nextInt(-200, maxY + 200).toFloat()
        val r = l + rnd.nextInt(-50, maxX + 200)
        val b = t + rnd.nextInt(-50, maxY + 200)
        return Rect(l, t, r, b)
    }

    @Test
    fun `swipePath stays inside region viewport and below the caption with the exact length and direction`() {
        var paths = 0
        var rejected = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val width = rnd.nextInt(0, 2400)
                val height = rnd.nextInt(0, 3200)
                val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
                val region = if (rnd.nextInt(4) == 0) viewport else saneRect(rnd, width, height)
                val top = rnd.nextInt(-100, height + 200).toFloat()
                val margin = rnd.nextInt(1, 120).toFloat()
                val guide = SwipeGuide(SwipeDirection.entries.random(rnd), SwipeDistance.entries.random(rnd))
                val label = "seed=$seed iteration=$iteration viewport=$viewport region=$region top=$top margin=$margin guide=$guide"
                val path = swipePath(guide, region, viewport, top, margin)
                val visible =
                    Rect(
                        maxOf(region.left, 0f),
                        maxOf(region.top, 0f, top),
                        minOf(region.right, width.toFloat()),
                        minOf(region.bottom, height.toFloat()),
                    )
                val areaWidth = visible.right - visible.left - 2 * margin
                val areaHeight = visible.bottom - visible.top - 2 * margin
                val vertical = guide.direction == SwipeDirection.UP || guide.direction == SwipeDirection.DOWN
                val half = (if (vertical) areaHeight else areaWidth) * guide.distance.fraction / 2f
                val roomy = areaWidth >= margin * 2 && areaHeight >= margin * 2 && half >= margin / 2f
                if (path == null) {
                    assertFalse(roomy, "rejected a roomy area: $label")
                    rejected++
                } else {
                    val points = listOf(path.start.x, path.start.y, path.end.x, path.end.y)
                    assertTrue(points.all { it.isFinite() }, label)
                    for (p in listOf(path.start, path.end)) {
                        assertTrue(p.x >= visible.left + margin - EPS && p.x <= visible.right - margin + EPS, "x outside: $label $p")
                        assertTrue(p.y >= visible.top + margin - EPS && p.y <= visible.bottom - margin + EPS, "y outside: $label $p")
                        assertTrue(p.x >= 0f && p.x <= width && p.y >= top && p.y <= height, "outside viewport: $label $p")
                        assertTrue(p.x >= region.left && p.x <= region.right && p.y >= region.top && p.y <= region.bottom, label)
                    }
                    assertTrue(abs(path.length - 2 * half) <= 0.01f * maxOf(1f, 2 * half), "length ${path.length} != ${2 * half}: $label")
                    assertTrue(path.length >= margin - EPS, "path shorter than the margin: $label")
                    val dx = path.end.x - path.start.x
                    val dy = path.end.y - path.start.y
                    when (guide.direction) {
                        SwipeDirection.UP -> assertTrue(dy < 0f && abs(dx) <= EPS, "not up: $label")
                        SwipeDirection.DOWN -> assertTrue(dy > 0f && abs(dx) <= EPS, "not down: $label")
                        SwipeDirection.LEFT -> assertTrue(dx < 0f && abs(dy) <= EPS, "not left: $label")
                        SwipeDirection.RIGHT -> assertTrue(dx > 0f && abs(dy) <= EPS, "not right: $label")
                    }
                    assertEquals(path, swipePath(guide, region, viewport, top, margin), "deterministic: $label")
                    paths++
                }
            }
        }
        assertTrue(paths > 0 && rejected > 0, "paths=$paths rejected=$rejected")
    }

    @Test
    fun `opposite directions share the same centre and distance and longer distances never shrink`() {
        var compared = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val width = rnd.nextInt(200, 2400)
                val height = rnd.nextInt(200, 3200)
                val viewport = Rect(0f, 0f, width.toFloat(), height.toFloat())
                val region = saneRect(rnd, width, height)
                val top = rnd.nextInt(0, height / 2).toFloat()
                val margin = rnd.nextInt(1, 60).toFloat()
                val label = "seed=$seed iteration=$iteration viewport=$viewport region=$region top=$top margin=$margin"
                for (distance in SwipeDistance.entries) {
                    val up = swipePath(SwipeGuide(SwipeDirection.UP, distance), region, viewport, top, margin)
                    val down = swipePath(SwipeGuide(SwipeDirection.DOWN, distance), region, viewport, top, margin)
                    val left = swipePath(SwipeGuide(SwipeDirection.LEFT, distance), region, viewport, top, margin)
                    val right = swipePath(SwipeGuide(SwipeDirection.RIGHT, distance), region, viewport, top, margin)
                    if (up != null && down != null) {
                        assertEquals(up.start, down.end, "$label $distance")
                        assertEquals(up.end, down.start, "$label $distance")
                    }
                    if (left != null && right != null) {
                        assertEquals(left.start, right.end, "$label $distance")
                        assertEquals(left.end, right.start, "$label $distance")
                    }
                }
                for (direction in SwipeDirection.entries) {
                    val lengths =
                        SwipeDistance.entries.map { swipePath(SwipeGuide(direction, it), region, viewport, top, margin)?.length }
                    assertTrue(
                        lengths[0] == null || (lengths[1] != null && lengths[1]!! >= lengths[0]!! - EPS),
                        "$label $direction medium missing or shorter than short: $lengths",
                    )
                    assertTrue(
                        lengths[1] == null || (lengths[2] != null && lengths[2]!! >= lengths[1]!! - EPS),
                        "$label $direction long missing or shorter than medium: $lengths",
                    )
                    compared++
                }
            }
        }
        assertTrue(compared > 0)
    }

    @Test
    fun `swipePath with non finite or extreme values never throws and only returns finite points`() {
        var nonNull = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val viewport =
                    if (rnd.nextBoolean()) {
                        Rect(0f, 0f, rnd.nextInt(0, 3000).toFloat(), rnd.nextInt(0, 3000).toFloat())
                    } else {
                        // The viewport comes from integer display metrics, so it is finite and bounded.
                        Rect(boundedFloat(rnd), boundedFloat(rnd), boundedFloat(rnd), boundedFloat(rnd))
                    }
                val region = Rect(anyFloat(rnd), anyFloat(rnd), anyFloat(rnd), anyFloat(rnd))
                val top = anyFloat(rnd)
                val margin = anyFloat(rnd)
                val guide = SwipeGuide(SwipeDirection.entries.random(rnd), SwipeDistance.entries.random(rnd))
                val label = "seed=$seed iteration=$iteration viewport=$viewport region=$region top=$top margin=$margin guide=$guide"
                val path = swipePath(guide, region, viewport, top, margin)
                if (!listOf(region.left, region.top, region.right, region.bottom, top, margin).all { it.isFinite() }) {
                    assertNull(path, "non finite input must be rejected: $label")
                }
                if (path != null && margin > 0f) {
                    val points = listOf(path.start.x, path.start.y, path.end.x, path.end.y)
                    assertTrue(points.all { it.isFinite() }, "non finite point: $label path=$path")
                    assertTrue(path.length.isFinite() && path.length > 0f, "degenerate length: $label path=$path")
                    nonNull++
                }
            }
        }
        assertTrue(nonNull > 0, "nonNull=$nonNull")
    }

    @Test
    fun `swipePath is null when the caption covers the whole region`() {
        val viewport = Rect(0f, 0f, 720f, 1600f)
        val region = Rect(0f, 0f, 720f, 800f)
        for (direction in SwipeDirection.entries) {
            for (distance in SwipeDistance.entries) {
                assertNull(swipePath(SwipeGuide(direction, distance), region, viewport, 800f, 24f))
                assertNull(swipePath(SwipeGuide(direction, distance), region, viewport, 1600f, 24f))
                assertNotNull(swipePath(SwipeGuide(direction, distance), region, viewport, 0f, 24f))
            }
        }
    }

    private fun scrollNode(
        rnd: Random,
        id: String,
        depth: Int = 0,
    ): AccessibilityNodeData {
        val l = rnd.nextInt(-30, 700)
        val t = rnd.nextInt(-30, 1500)
        return AccessibilityNodeData(
            id = id,
            className = "androidx.recyclerview.widget.RecyclerView",
            text = FuzzStrings.validUtf16(rnd, 20),
            contentDescription = null,
            resourceId = null,
            bounds = BoundsData(l, t, l + rnd.nextInt(-20, 400), t + rnd.nextInt(-20, 900)),
            clickable = rnd.nextBoolean(),
            longClickable = rnd.nextBoolean(),
            focusable = rnd.nextBoolean(),
            scrollable = rnd.nextInt(3) != 0,
            editable = rnd.nextInt(8) == 0,
            enabled = rnd.nextInt(4) != 0,
            visible = rnd.nextInt(4) != 0,
            webRole = null,
            children = if (depth >= 3) emptyList() else List(rnd.nextInt(0, 3)) { scrollNode(rnd, "$id.$it", depth + 1) },
        )
    }

    private fun window(
        rnd: Random,
        index: Int,
    ): WindowData =
        WindowData(
            windowId = index,
            windowType = WINDOW_TYPES[rnd.nextInt(WINDOW_TYPES.size)],
            packageName = "pkg$index",
            title = FuzzStrings.validUtf16(rnd, 15),
            activityName = null,
            layer = index,
            focused = index == 0,
            tree = scrollNode(rnd, "w$index"),
        )

    private fun expected(
        result: MultiWindowResult,
        options: ScreenStateFormatOptions,
        selector: (AccessibilityNodeData) -> Boolean,
    ): Map<String, Rect> {
        val expected = linkedMapOf<String, Rect>()

        fun walk(node: AccessibilityNodeData) {
            if (formatter.isListed(node, options) && node.visible && node.enabled && selector(node)) {
                val b = node.bounds
                val rect = Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
                if (rect.left >= 0 && rect.top >= 0 && rect.width > 0 && rect.height > 0) expected[node.id] = rect
            }
            node.children.forEach(::walk)
        }
        result.windows.filter { it.windowType == "APPLICATION" }.forEach { walk(it.tree) }
        return expected
    }

    @Test
    fun `scroll nodes equal the real formatter's enabled visible scrollable rows of application windows`() {
        var withScroll = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val result = MultiWindowResult(List(rnd.nextInt(1, 4)) { window(rnd, it) }, false)
                val options = ScreenStateFormatOptions(rnd.nextBoolean(), rnd.nextBoolean())
                val text = formatter.formatMultiWindow(result, screenInfo, options)
                val parsed = parseHelperScreen(text)
                val label = "seed=$seed iteration=$iteration text=${FuzzStrings.escape(text).take(6000)}"
                assertEquals(expected(result, options) { it.scrollable }, parsed.scrollNodes, label)
                assertEquals(expected(result, options) { it.clickable }, parsed.nodes, label)
                parsed.scrollNodes.values.forEach {
                    assertTrue(it.left >= 0f && it.top >= 0f && it.width > 0f && it.height > 0f, "$label $it")
                }
                if (parsed.scrollNodes.isNotEmpty()) withScroll++
            }
        }
        assertTrue(withScroll > 0, "withScroll=$withScroll")
    }

    @Test
    fun `rows with random flags and bounds fill nodes and scrollNodes exactly per flag`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val l = rnd.nextInt(-20, 700)
                val t = rnd.nextInt(-20, 1500)
                val r = rnd.nextInt(-20, 900)
                val b = rnd.nextInt(-20, 1700)
                val flagSet = listOf("on", "off", "clk", "lclk", "foc", "scr", "edt", "ena").filter { rnd.nextBoolean() }
                val appWindow = rnd.nextInt(5) != 0
                val text =
                    "--- window:1 type:${if (appWindow) "APPLICATION" else "SYSTEM"} pkg:p title:t layer:0 focused:true ---\n" +
                        "n1\tList\tText\t-\t-\t$l,$t,$r,$b\t${flagSet.joinToString(",")}"
                val base = appWindow && "on" in flagSet && "ena" in flagSet && l >= 0 && t >= 0 && r > l && b > t
                val rect = Rect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
                val parsed = parseHelperScreen(text)
                val label = "seed=$seed iteration=$iteration text=${FuzzStrings.escape(text)}"
                assertEquals(if (base && "clk" in flagSet) mapOf("n1" to rect) else emptyMap<String, Rect>(), parsed.nodes, label)
                assertEquals(if (base && "scr" in flagSet) mapOf("n1" to rect) else emptyMap<String, Rect>(), parsed.scrollNodes, label)
            }
        }
    }

    @Test
    fun `mutated rows never throw and scroll rects always have strict integer bounds`() {
        val strict = Regex("-?\\d+,-?\\d+,-?\\d+,-?\\d+")
        val tokens = listOf("1", "10", "200", "", "x", "NaN", "Infinity", "1e2", "10f", "0x10", " 5", "-5", "5.5", "+5", "99999999999")
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val bounds = List(rnd.nextInt(2, 7)) { tokens[rnd.nextInt(tokens.size)] }.joinToString(",")
                val text =
                    "--- window:1 type:APPLICATION pkg:p title:t layer:0 focused:true ---\n" +
                        "n1\tList\tT\t-\t-\t$bounds\ton,scr,ena"
                val parsed = parseHelperScreen(text)
                if (parsed.scrollNodes.isNotEmpty()) {
                    assertTrue(strict.matches(bounds), "seed=$seed iteration=$iteration accepted '$bounds'")
                }
            }
        }
    }

    private fun scrollOnlyScreen(): HelperScreen =
        HelperScreen(
            text = "screen",
            nodes = mapOf("tap1" to Rect(10f, 20f, 200f, 80f), "both" to Rect(0f, 0f, 50f, 50f)),
            scrollNodes = mapOf("list1" to Rect(0f, 100f, 720f, 1500f), "both" to Rect(0f, 0f, 50f, 50f)),
            signature = "signature",
        )

    private fun field(
        name: String,
        value: Any?,
        present: Boolean,
    ): String =
        if (!present) {
            ""
        } else {
            when (value) {
                null -> "\"$name\":null"
                is String -> "\"$name\":\"$value\""
                else -> "\"$name\":$value"
            }
        }

    @Test
    fun `swipe replies decode exactly when gesture category node direction and distance are all valid`() {
        val screen = scrollOnlyScreen()
        val gestures = listOf("tap", "swipe", "Swipe", "", "drag", "edge", "SWIPE")
        val categories = listOf("other", "payment", "transmission")
        val nodeIds = listOf("tap1", "list1", "both", "missing", "")
        val directions = listOf(null, "up", "down", "left", "right", "UP", "Up", "diagonal", "", "north")
        val distances = listOf(null, "short", "medium", "long", "SHORT", "far", "", "Medium")
        var answers = 0
        var swipes = 0
        var rejections = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val gesture = gestures[rnd.nextInt(gestures.size)]
                val gesturePresent = rnd.nextInt(5) != 0
                val category = categories[rnd.nextInt(categories.size)]
                val nodeChoice = rnd.nextInt(7)
                val nodeId = if (nodeChoice == 5) null else nodeIds[rnd.nextInt(nodeIds.size)]
                val nodePresent = nodeChoice != 6
                val direction = directions[rnd.nextInt(directions.size)]
                val directionPresent = rnd.nextInt(4) != 0
                val distance = distances[rnd.nextInt(distances.size)]
                val distancePresent = rnd.nextInt(4) != 0
                val parts =
                    listOf(
                        field("nodeId", nodeId, nodePresent),
                        "\"explanation\":\"설명이에요\"",
                        "\"action\":\"행동\"",
                        "\"category\":\"$category\"",
                        field("gesture", gesture, gesturePresent),
                        field("direction", direction, directionPresent),
                        field("distance", distance, distancePresent),
                    ).filter { it.isNotEmpty() }.shuffled(rnd)
                val text = parts.joinToString(",", "{", "}")
                val label = "seed=$seed iteration=$iteration json=$text"
                val effectiveGesture = if (gesturePresent) gesture else "tap"
                val effectiveDirection = if (directionPresent) direction else null
                val effectiveDistance = if (distancePresent) distance else null
                val effectiveNode = if (nodePresent) nodeId else null
                val result = runCatching { decodeHelperAnswer(text, screen) }
                result.exceptionOrNull()?.let { assertTrue(it is HelperGuideException, "$label unexpected $it") }
                val expectedSwipe =
                    if (effectiveGesture == "swipe") {
                        val d = SwipeDirection.entries.firstOrNull { it.name.lowercase() == effectiveDirection }
                        val s = SwipeDistance.entries.firstOrNull { it.name.lowercase() == effectiveDistance }
                        if (category == "other" && effectiveNode != null && d != null && s != null) SwipeGuide(d, s) else null
                    } else {
                        null
                    }
                val valid =
                    when (effectiveGesture) {
                        "swipe" -> {
                            expectedSwipe != null && effectiveNode in screen.scrollNodes
                        }

                        "tap" -> {
                            effectiveDirection == null && effectiveDistance == null &&
                                (effectiveNode == null || effectiveNode in screen.nodes)
                        }

                        else -> {
                            false
                        }
                    }
                assertEquals(valid, result.isSuccess, label)
                if (valid) {
                    val step = result.getOrThrow().step
                    assertEquals(expectedSwipe, step.swipe, label)
                    if (expectedSwipe != null) {
                        assertEquals(screen.scrollNodes[effectiveNode], step.target, label)
                        assertFalse(step.needsExplanation, "swipe must never need confirmation: $label")
                        assertEquals(GuideAction.OTHER, step.kind, label)
                        swipes++
                    } else {
                        assertEquals(effectiveNode?.let { screen.nodes[it] } ?: Rect.Zero, step.target, label)
                    }
                    assertFalse(step.confirmed, label)
                    answers++
                } else {
                    rejections++
                }
            }
        }
        assertTrue(answers > 0 && swipes > 0 && rejections > 0, "answers=$answers swipes=$swipes rejections=$rejections")
    }

    private companion object {
        val SEEDS = listOf(1L, 2L, 3L, 42L, 2024L, 99991L)
        const val ITERATIONS = 400
        const val EPS = 0.01f
        val WINDOW_TYPES = listOf("APPLICATION", "APPLICATION", "INPUT_METHOD", "SYSTEM", "UNKNOWN")
    }
}
