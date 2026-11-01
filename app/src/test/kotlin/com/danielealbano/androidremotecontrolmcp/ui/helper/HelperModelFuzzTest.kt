package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Seeded random event sequences against the helper entry flow's state machine. */
class HelperModelFuzzTest {
    @Test
    fun `entry model follows the practice order and every event is stable`() {
        val order = HelperPracticeStep.entries
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                var model = HelperEntryModel()
                repeat(rnd.nextInt(1, 40)) { step ->
                    val before = model
                    val event = rnd.nextInt(5)
                    model =
                        when (event) {
                            0 -> before.practice()
                            1, 2 -> before.advance()
                            3 -> before.copy(page = HelperEntryPage.entries[rnd.nextInt(HelperEntryPage.entries.size)])
                            else -> before.copy(page = HelperEntryPage.HOME)
                        }
                    val label = "seed=$seed iteration=$iteration step=$step event=$event before=$before after=$model"
                    when (event) {
                        0 -> {
                            val expected = HelperEntryModel(HelperEntryPage.PRACTICE, HelperPracticeStep.OPEN)
                            assertEquals(expected, model, label)
                            assertEquals(model, model.practice(), "practice idempotent: $label")
                        }

                        1, 2 -> {
                            if (before.page != HelperEntryPage.PRACTICE) {
                                assertEquals(before, model, "advance outside practice must be a no-op: $label")
                            } else if (before.step != HelperPracticeStep.DONE) {
                                assertEquals(HelperEntryPage.PRACTICE, model.page, label)
                                assertEquals(order[order.indexOf(before.step) + 1], model.step, label)
                            } else {
                                val expected = HelperEntryModel(HelperEntryPage.INVITE, HelperPracticeStep.DONE)
                                assertEquals(expected, model, label)
                                assertEquals(model, model.advance(), "advance after invite is stable: $label")
                            }
                        }

                        else -> {
                            assertEquals(before.step, model.step, label)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `four advances from a fresh practice reach invite and never beyond`() {
        var model = HelperEntryModel().practice()
        repeat(4) { model = model.advance() }
        assertEquals(HelperEntryPage.INVITE, model.page)
        repeat(10) { model = model.advance() }
        assertEquals(HelperEntryModel(HelperEntryPage.INVITE, HelperPracticeStep.DONE), model)
    }

    private companion object {
        val SEEDS = listOf(1L, 2L, 3L, 42L, 2024L, 99991L)
        const val ITERATIONS = 400
    }
}
