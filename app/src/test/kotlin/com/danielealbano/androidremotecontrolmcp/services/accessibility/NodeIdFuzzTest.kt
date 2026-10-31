package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("AccessibilityTreeParser.generateNodeId property tests")
class NodeIdFuzzTest {
    private val parser = AccessibilityTreeParser()
    private val idShape = Regex("^node_[0-9a-f]{1,8}$")

    private val classes =
        listOf(
            null,
            "android.widget.TextView",
            "android.widget.Button",
            "android.view.View",
            "android.widget.FrameLayout",
            "androidx.recyclerview.widget.RecyclerView",
        )

    private fun node(
        resourceId: String?,
        className: String?,
    ): AccessibilityNodeInfo {
        val info = mockk<AccessibilityNodeInfo>()
        every { info.viewIdResourceName } returns resourceId
        every { info.className } returns className
        return info
    }

    private val pool: List<Pair<AccessibilityNodeInfo, String>> =
        List(POOL_SIZE) { index ->
            val resourceId = if (index % 3 == 0) null else "com.example:id/item$index"
            val className = classes[index % classes.size]
            node(resourceId, className) to "${resourceId.orEmpty()}|${className.orEmpty()}"
        }

    private class Counters {
        var nodes = 0L
        var distinctInputs = 0L
        var collisions = 0L
        var expected = 0.0
    }

    private fun buildScreen(
        rnd: Random,
        counters: Counters,
    ) {
        val inputToId = HashMap<String, String>()
        val idToInput = HashMap<String, String>()
        var budget = NODES_PER_SCREEN

        fun visit(
            parentId: String,
            depth: Int,
            index: Int,
        ) {
            if (budget <= 0) return
            budget--
            val (info, nodeKey) = pool[rnd.nextInt(pool.size)]
            val left = rnd.nextInt(0, 135) * 8
            val top = rnd.nextInt(0, 300) * 8
            val bounds = BoundsData(left, top, left + rnd.nextInt(1, 100) * 8, top + rnd.nextInt(1, 60) * 8)
            val id = parser.generateNodeId(info, bounds, depth, index, parentId)
            val input = "$nodeKey|${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}|$depth|$index|$parentId"
            counters.nodes++
            assertTrue(idShape.matches(id), "malformed id '$id'")
            val again = parser.generateNodeId(info, bounds, depth, index, parentId)
            assertEquals(id, again, "not deterministic for $input")
            val previousId = inputToId.putIfAbsent(input, id)
            if (previousId != null) {
                assertEquals(previousId, id, "same input produced two ids: $input")
            } else {
                counters.distinctInputs++
                val owner = idToInput.putIfAbsent(id, input)
                if (owner != null) counters.collisions++
            }
            if (depth < MAX_DEPTH) {
                repeat(rnd.nextInt(0, 6)) { child -> visit(id, depth + 1, child) }
            }
        }

        visit("root", 0, 0)
        while (budget > 0) visit("root", 0, rnd.nextInt(1, 50))
        val distinct = inputToId.size.toDouble()
        counters.expected += distinct * (distinct - 1) / 2.0 / TWO_POW_32
    }

    @Test
    fun `ids are deterministic and well-formed and collisions stay within the 32-bit birthday bound`() {
        val seed = 0x4E0DE1L
        val rnd = Random(seed)
        val counters = Counters()
        repeat(SCREENS) {
            buildScreen(rnd, counters)
            // MockK records every call; drop the recordings (not the stubs) so memory stays flat
            pool.forEach { clearMocks(it.first, answers = false) }
        }
        println(
            "NODE_ID_COLLISION_DATA seed=$seed screens=$SCREENS nodes=${counters.nodes} " +
                "distinctInputs=${counters.distinctInputs} collisions=${counters.collisions} " +
                "expectedUnderUniformHash=${"%.2f".format(counters.expected)} " +
                "ratePerDistinctInput=${counters.collisions.toDouble() / counters.distinctInputs}",
        )
        assertTrue(
            counters.collisions <= counters.expected * SLACK + SLACK_ABSOLUTE,
            "collisions=${counters.collisions} expected~${counters.expected} (structured inputs cluster)",
        )
    }

    private companion object {
        const val POOL_SIZE = 60
        const val SCREENS = 20
        const val NODES_PER_SCREEN = 5_000
        const val MAX_DEPTH = 12
        const val TWO_POW_32 = 4_294_967_296.0
        const val SLACK = 5.0
        const val SLACK_ABSOLUTE = 5.0
    }
}
