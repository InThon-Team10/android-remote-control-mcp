package com.danielealbano.androidremotecontrolmcp.services.accessibility

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("WebViewNodeMerger property tests")
class WebViewNodeMergerFuzzTest {
    private val merger = WebViewNodeMerger()
    private val tokenRegex = Regex("[\\w/.:-]+|\\S")
    private val roles =
        listOf("article", "listItem", "list", "heading", "image", "link", "paragraph", "staticText", "generic", "main")
    private val words = listOf("alpha", "Beta", "7", "+", "★", "a.b", "x-y", "https://e.x/p", "OK", "ok", "a", "😀")

    // Tokens that are only invisible whitespace (e.g. U+001F, which Kotlin trims) carry no content to preserve
    private fun tokens(text: String?): Set<String> =
        tokenRegex
            .findAll(text.orEmpty().lowercase())
            .map { it.value }
            .filterNot { token -> token.all { it.isWhitespace() } }
            .toSet()

    private fun phrase(rnd: Random): String? =
        when (rnd.nextInt(5)) {
            0 -> null
            1 -> ""
            2 -> FuzzStrings.random(rnd, 8)
            else -> List(rnd.nextInt(1, 4)) { words[rnd.nextInt(words.size)] }.joinToString(" ")
        }

    private var nextId = 0

    private fun randomNode(
        rnd: Random,
        depth: Int,
        web: Boolean,
    ): AccessibilityNodeData {
        val children = if (depth <= 0) emptyList() else List(rnd.nextInt(0, 4)) { randomNode(rnd, depth - 1, web) }
        val interactive = rnd.nextInt(5) == 0
        return AccessibilityNodeData(
            id = "id${nextId++}",
            text = phrase(rnd),
            contentDescription = phrase(rnd),
            bounds = BoundsData(0, 0, nextId, nextId),
            clickable = interactive && rnd.nextBoolean(),
            longClickable = interactive && rnd.nextBoolean(),
            editable = interactive && rnd.nextBoolean(),
            scrollable = interactive && rnd.nextBoolean(),
            webRole = if (web) roles[rnd.nextInt(roles.size)] else null,
            targetUrl = if (rnd.nextBoolean()) words[rnd.nextInt(words.size)] else null,
            children = children,
        )
    }

    private fun all(node: AccessibilityNodeData): List<AccessibilityNodeData> =
        buildList {
            add(node)
            node.children.forEach { addAll(all(it)) }
        }

    private fun isInteractive(node: AccessibilityNodeData): Boolean {
        val tapFlags = listOf(node.clickable, node.longClickable, node.editable, node.scrollable)
        return tapFlags.any { it }
    }

    private fun result(tree: AccessibilityNodeData): MultiWindowResult {
        val window = WindowData(1, "APPLICATION", tree = tree)
        return MultiWindowResult(listOf(window))
    }

    @Test
    fun `no text or image or link url is lost and every tap target survives with its id and bounds`() {
        val seed = 0x3E8001L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val web = randomNode(rnd, rnd.nextInt(0, 6), web = true)
            val root = web.copy(webRole = roles[rnd.nextInt(roles.size)])
            val ctx = "seed=$seed iteration=$i"
            val merged = merger.merge(result(root)).windows[0].tree
            val before = all(root)
            val after = all(merged)
            val tokensAfter = after.flatMap { tokens(it.text) + tokens(it.contentDescription) }.toSet()
            for (node in before) {
                for (token in tokens(node.text) + tokens(node.contentDescription)) {
                    assertTrue(token in tokensAfter, "$ctx lost token '${FuzzStrings.escape(token)}' of ${node.id}")
                }
                if (node.webRole == "image" || node.webRole == "link") {
                    for (token in tokens(node.targetUrl?.trim())) {
                        assertTrue(token in tokensAfter, "$ctx lost url token '$token' of ${node.id}")
                    }
                }
            }
            val afterById = after.associateBy { it.id }
            assertEquals(after.size, afterById.size, "$ctx duplicate ids after merge")
            for (node in before.filter { isInteractive(it) }) {
                val kept = afterById[node.id]
                assertTrue(kept != null, "$ctx tap target ${node.id} dropped")
                assertEquals(node.bounds, kept!!.bounds, "$ctx ${node.id} bounds")
                assertEquals(isInteractive(node), isInteractive(kept), "$ctx ${node.id} flags")
            }
            assertTrue(after.size <= before.size, "$ctx grew from ${before.size} to ${after.size}")
        }
    }

    @Test
    fun `native trees are returned by reference and native siblings of a web area keep their instances`() {
        val seed = 0x3E8002L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val ctx = "seed=$seed iteration=$i"
            val native = randomNode(rnd, rnd.nextInt(0, 5), web = false)
            val nativeResult = result(native)
            assertSame(nativeResult, merger.merge(nativeResult), ctx)
            val mixed = native.copy(children = native.children + randomNode(rnd, rnd.nextInt(0, 4), web = true))
            val merged = merger.merge(result(mixed)).windows[0].tree
            assertEquals(mixed.children.size, merged.children.size, ctx)
            native.children.forEachIndexed { index, child ->
                assertSame(child, merged.children[index], "$ctx child $index")
            }
            assertEquals(mixed.id, merged.id, ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 4000
    }
}
