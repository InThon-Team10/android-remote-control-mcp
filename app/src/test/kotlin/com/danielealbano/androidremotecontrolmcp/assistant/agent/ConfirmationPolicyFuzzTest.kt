package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("ConfirmationPolicy fuzz")
class ConfirmationPolicyFuzzTest {
    private val gated = AssistantConfig.CONFIRMATION_TOOLS.toList()
    private val slugChars = "abcXYZ019_"

    private fun slug(random: Random): String =
        buildString { repeat(random.nextInt(8)) { append(slugChars[random.nextInt(slugChars.length)]) } }

    private fun mangle(
        random: Random,
        name: String,
    ): String =
        when (random.nextInt(9)) {
            0 -> name.uppercase()
            1 -> name.replaceFirstChar { it.uppercaseChar() }
            2 -> " $name"
            3 -> "$name "
            4 -> name + "\u200b"
            5 -> name.replace('e', '\u0435')
            6 -> name.replace('a', '\uff41')
            7 -> name.replace("_", "\u2010")
            else -> name.dropLast(1)
        }

    @Test
    fun `every gated tool of every slug requires confirmation`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(100) {
                val slug = slug(random)
                val prefix = McpToolUtils.buildToolNamePrefix(slug)
                for (tool in gated) {
                    val required = ConfirmationPolicy.requiresConfirmation(prefix + tool, slug)
                    assertTrue(required, "seed=$seed slug=$slug tool=$tool")
                }
            }
        }
    }

    @Test
    fun `random names are gated exactly when foreign-prefixed or an exact gated name`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(300) { index ->
                val slug = slug(random)
                val prefix = McpToolUtils.buildToolNamePrefix(slug)
                val name =
                    when (random.nextInt(4)) {
                        0 -> prefix + AssistantFuzzGen.string(random, 14)
                        1 -> mangle(random, prefix + gated[random.nextInt(gated.size)])
                        2 -> prefix + mangle(random, gated[random.nextInt(gated.size)])
                        else -> AssistantFuzzGen.string(random, 20)
                    }
                val expected = !name.startsWith(prefix) || name.removePrefix(prefix) in gated
                val actual = ConfirmationPolicy.requiresConfirmation(name, slug)
                assertEquals(expected, actual, "seed=$seed index=$index name=$name")
            }
        }
    }

    @Test
    fun `a name of another slug is gated when the configured slug is not a prefix of it`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(100) {
                val configured = slug(random)
                val other = slug(random)
                val prefix = McpToolUtils.buildToolNamePrefix(configured)
                val foreign = McpToolUtils.buildToolNamePrefix(other) + gated[random.nextInt(gated.size)]
                if (!foreign.startsWith(prefix)) {
                    val required = ConfirmationPolicy.requiresConfirmation(foreign, configured)
                    assertTrue(required, "seed=$seed $configured/$other")
                }
            }
        }
    }
}
