package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Seeded property tests for [decodeHelperAnswer]: every outcome is a [HelperAnswer] or a [HelperGuideException]. */
class HelperAnswerDecodeFuzzTest {
    private val screen =
        HelperScreen(
            text = "screen",
            nodes = mapOf("1:1" to Rect(10f, 20f, 200f, 80f), "n2" to Rect(1f, 1f, 5f, 9f), "answer" to Rect(3f, 3f, 4f, 4f)),
            signature = "signature",
        )

    private sealed interface Field {
        data object Absent : Field

        data class Raw(
            val json: String,
        ) : Field
    }

    private data class TextCase(
        val field: Field,
        val value: String?,
        val valid: Boolean,
    )

    private fun str(value: String): Field = Field.Raw(JsonPrimitive(value).toString())

    private fun textField(
        rnd: Random,
        limit: Int,
    ): TextCase =
        when (rnd.nextInt(12)) {
            0 -> {
                TextCase(Field.Absent, null, false)
            }

            1 -> {
                TextCase(Field.Raw("null"), null, false)
            }

            2 -> {
                TextCase(Field.Raw(rnd.nextInt().toString()), null, false)
            }

            3 -> {
                TextCase(Field.Raw("true"), null, false)
            }

            4 -> {
                TextCase(Field.Raw("[\"x\"]"), null, false)
            }

            5 -> {
                TextCase(Field.Raw("{\"a\":1}"), null, false)
            }

            6 -> {
                val blank = listOf("", " ", "\t\n", " ", " ").random(rnd)
                TextCase(str(blank), blank, false)
            }

            7, 8 -> {
                val value = longText(rnd, limit + rnd.nextInt(-2, 3))
                TextCase(str(value), value, value.isNotBlank() && value.length <= limit)
            }

            else -> {
                val value = FuzzStrings.validUtf16(rnd, 120)
                TextCase(str(value), value, value.isNotBlank() && value.length <= limit)
            }
        }

    private fun longText(
        rnd: Random,
        length: Int,
    ): String {
        val sb = StringBuilder()
        while (sb.length < length) sb.append(if (rnd.nextInt(4) == 0) "😀" else "a")
        val cut = sb.toString().take(length)
        return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) + "b" else cut
    }

    private fun json(fields: List<Pair<String, Field>>): String =
        fields
            .mapNotNull { (name, field) -> (field as? Field.Raw)?.let { "\"$name\":${it.json}" } }
            .joinToString(",", "{", "}")

    @Test
    fun `random replies decode to the expected answer or a typed exception`() {
        var answers = 0
        var rejections = 0
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val explanation = textField(rnd, 3000)
                val action = textField(rnd, 500)
                val categoryChoice = rnd.nextInt(8)
                val category = listOf("payment", "transmission", "other")[rnd.nextInt(3)]
                val categoryField =
                    when (categoryChoice) {
                        0 -> Field.Absent
                        1 -> Field.Raw("null")
                        2 -> Field.Raw("3")
                        3 -> str("Payment")
                        4 -> str("")
                        else -> str(category)
                    }
                val categoryOk = categoryChoice >= 5
                val nodeChoice = rnd.nextInt(7)
                val nodeIds = listOf("1:1", "n2", "answer")
                val nodeId = nodeIds[rnd.nextInt(nodeIds.size)]
                val nodeField =
                    when (nodeChoice) {
                        0 -> Field.Absent
                        1 -> Field.Raw("null")
                        2 -> Field.Raw("11")
                        3 -> str("")
                        4 -> str("unknown-${rnd.nextInt()}")
                        else -> str(nodeId)
                    }
                val nodeOk = nodeChoice <= 1 || nodeChoice >= 5
                val unknown =
                    if (rnd.nextBoolean()) {
                        listOf("extra" to Field.Raw("{\"deep\":[1,2,{\"x\":null}]}"), "note" to str(FuzzStrings.validUtf16(rnd, 20)))
                    } else {
                        emptyList()
                    }
                val fields =
                    (
                        listOf(
                            "nodeId" to nodeField,
                            "explanation" to explanation.field,
                            "action" to action.field,
                            "category" to categoryField,
                        ) + unknown
                    ).shuffled(rnd)
                val text = json(fields)
                val label = "seed=$seed iteration=$iteration json=${FuzzStrings.escape(text).take(400)}"
                val result = runCatching { decodeHelperAnswer(text, screen) }
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    assertTrue(failure is HelperGuideException, "$label unexpected ${failure::class}: ${failure.message}")
                }
                val expectAnswer = explanation.valid && action.valid && categoryOk && nodeOk
                assertEquals(expectAnswer, result.isSuccess, label)
                if (expectAnswer) {
                    val answer = result.getOrThrow()
                    val step = answer.step
                    val expectedNode = if (nodeChoice >= 5) nodeId else null
                    assertEquals("AI 안내: ${explanation.value}", step.result, label)
                    assertEquals(action.value, step.action, label)
                    assertEquals(expectedNode ?: "answer", step.id, label)
                    assertEquals(expectedNode?.let { screen.nodes[it] } ?: Rect.Zero, step.target, label)
                    assertEquals(screen.signature, answer.signature, label)
                    assertFalse(step.confirmed, label)
                    assertEquals(category != "other", step.needsExplanation, label)
                    assertEquals(1, step.facts.size, label)
                    answers++
                } else {
                    rejections++
                }
            }
        }
        assertTrue(answers > 0 && rejections > 0, "answers=$answers rejections=$rejections")
    }

    @Test
    fun `wrapped truncated and garbled replies only produce an answer or a typed exception`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val candidate =
                    when (rnd.nextInt(8)) {
                        0 -> {
                            "```json\n$VALID\n```"
                        }

                        1 -> {
                            "다음과 같아요: $VALID"
                        }

                        2 -> {
                            VALID.substring(0, rnd.nextInt(VALID.length))
                        }

                        3 -> {
                            VALID + FuzzStrings.random(rnd, 30)
                        }

                        4 -> {
                            "$VALID"
                        }

                        5 -> {
                            FuzzStrings.random(rnd, 200)
                        }

                        6 -> {
                            val start = rnd.nextInt(VALID.length)
                            VALID.replaceRange(start, start + rnd.nextInt(VALID.length - start), FuzzStrings.random(rnd, 4))
                        }

                        else -> {
                            "[$VALID]"
                        }
                    }
                val label = "seed=$seed iteration=$iteration candidate=${FuzzStrings.escape(candidate).take(300)}"
                val failure = runCatching { decodeHelperAnswer(candidate, screen) }.exceptionOrNull()
                if (failure != null) assertTrue(failure is HelperGuideException, "$label ${failure::class}: ${failure.message}")
            }
        }
    }

    @Test
    fun `every proper prefix of a valid reply is rejected with a typed exception`() {
        assertEquals("1:1", decodeHelperAnswer(VALID, screen).step.id)
        for (end in 0 until VALID.length) {
            val failure = runCatching { decodeHelperAnswer(VALID.substring(0, end), screen) }.exceptionOrNull()
            assertTrue(failure is HelperGuideException, "prefix=$end failure=$failure")
        }
    }

    @Test
    fun `deeply nested unknown fields do not escape as raw errors`() {
        for (depth in listOf(10, 1_000, 20_000, 200_000)) {
            val nested = "[".repeat(depth) + "]".repeat(depth)
            val text = """{"extra":$nested,"nodeId":null,"explanation":"설명","action":"행동","category":"other"}"""
            val failure = runCatching { decodeHelperAnswer(text, screen) }.exceptionOrNull()
            assertTrue(failure == null || failure is HelperGuideException, "depth=$depth failure=${failure?.let { it::class }}")
        }
    }

    private companion object {
        val SEEDS = listOf(1L, 2L, 3L, 42L, 2024L, 99991L)
        const val ITERATIONS = 250
        const val VALID = """{"nodeId":"1:1","explanation":"검색 화면으로 이동할 수 있어요.","action":"검색 버튼을 눌러 주세요.","category":"other"}"""
    }
}
