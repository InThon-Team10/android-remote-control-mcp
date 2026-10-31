package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/** Seeded random event sequences against the helper state machines. */
class HelperModelFuzzTest {
    @AfterEach
    fun resetRuntime() {
        HelperRuntime.disconnect()
        HelperRuntime.suppress(false)
    }

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
                            assertEquals(HelperEntryModel(HelperEntryPage.PRACTICE, HelperPracticeStep.OPEN), model, label)
                            assertEquals(model, model.practice(), "practice idempotent: $label")
                        }

                        1, 2 -> {
                            if (before.page != HelperEntryPage.PRACTICE) {
                                assertEquals(before, model, "advance outside practice must be a no-op: $label")
                            } else if (before.step != HelperPracticeStep.DONE) {
                                assertEquals(HelperEntryPage.PRACTICE, model.page, label)
                                assertEquals(order[order.indexOf(before.step) + 1], model.step, label)
                            } else {
                                assertEquals(HelperEntryModel(HelperEntryPage.INVITE, HelperPracticeStep.DONE), model, label)
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

    private fun randomRect(rnd: Random): Rect =
        when (rnd.nextInt(4)) {
            0 -> Rect.Zero
            1 -> Rect(rnd.nextFloat() * 100, rnd.nextFloat() * 100, 200f + rnd.nextFloat() * 100, 300f)
            else -> Rect(rnd.nextInt(0, 500).toFloat(), rnd.nextInt(0, 500).toFloat(), 600f, 900f)
        }

    private fun randomStep(rnd: Random): ConsequenceStep {
        val kind = GuideAction.entries[rnd.nextInt(GuideAction.entries.size)]
        return ConsequenceStep(
            id = "s${rnd.nextInt(3)}",
            action = "a${rnd.nextInt(3)}",
            result = "r${rnd.nextInt(3)}",
            facts = List(rnd.nextInt(0, 3)) { "f$it" },
            important = rnd.nextBoolean(),
            confirmed = rnd.nextBoolean(),
            target = randomRect(rnd),
            version = rnd.nextInt(0, 3),
            kind = kind,
        )
    }

    private data class Snapshot(
        val phase: ConsequencePhase,
        val step: ConsequenceStep?,
        val canLocate: Boolean,
        val measured: Boolean,
    )

    private fun ConsequenceState.snapshot() = Snapshot(phase, step, canLocate, measured)

    @Test
    fun `consequence state keeps its invariants and never opens an explanation step without locate`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val state = ConsequenceState()
                var located = false
                repeat(rnd.nextInt(1, 60)) { index ->
                    val op = rnd.nextInt(8)
                    val before = state.snapshot()
                    var changedStep = false
                    var idempotent: (() -> Unit)? = null
                    when (op) {
                        0 -> {
                            val step = randomStep(rnd)
                            state.begin(step)
                            located = false
                            changedStep = true
                            idempotent = { state.begin(step) }
                        }

                        1 -> {
                            val step = randomStep(rnd)
                            if (state.step != null) {
                                state.replace(step)
                                if (step != before.step) located = false
                                changedStep = step != before.step
                                idempotent = { state.replace(step) }
                            }
                        }

                        2 -> {
                            state.finish()
                            located = false
                            idempotent = { state.finish() }
                        }

                        3, 4 -> {
                            val fits = rnd.nextBoolean()
                            state.fit(fits)
                            idempotent = { state.fit(fits) }
                        }

                        5 -> {
                            if (state.canLocate && state.measured) located = true
                            state.locate()
                            idempotent = { state.locate() }
                        }

                        6 -> {
                            val step = state.step
                            if (step != null) {
                                state.replace(step.copy(version = step.version + 1))
                                located = false
                                changedStep = true
                            }
                        }

                        else -> {
                            val step = state.step
                            if (step != null) {
                                state.measured = false
                                state.phase = if (step.needsExplanation) ConsequencePhase.EXPLANATION else ConsequencePhase.TARGET
                                located = false
                            }
                        }
                    }
                    val after = state.snapshot()
                    val label = "seed=$seed iteration=$iteration index=$index op=$op before=$before after=$after"
                    if (state.step == null) {
                        assertEquals(ConsequencePhase.INPUT, state.phase, label)
                        assertFalse(state.canLocate, label)
                        assertFalse(state.targetOpen, label)
                    } else {
                        assertTrue(state.phase != ConsequencePhase.INPUT, label)
                    }
                    if (state.targetOpen) {
                        assertTrue(state.phase == ConsequencePhase.TARGET && state.measured && state.canLocate, label)
                        val step = checkNotNull(state.step) { label }
                        if (step.needsExplanation) assertTrue(located, "explanation step opened without locate: $label")
                    }
                    if (changedStep) {
                        assertFalse(state.measured || state.canLocate || state.targetOpen, "new step must re-measure: $label")
                        val step = checkNotNull(state.step) { label }
                        val expected =
                            when {
                                !step.needsExplanation -> ConsequencePhase.TARGET
                                op == 0 -> ConsequencePhase.EXPLANATION
                                else -> ConsequencePhase.RECONFIRM
                            }
                        assertEquals(expected, state.phase, label)
                    }
                    idempotent?.let {
                        val settled = state.snapshot()
                        it()
                        assertEquals(settled, state.snapshot(), "repeating the event changed the state: $label")
                    }
                }
            }
        }
    }

    @Test
    fun `fit without a step never allows locating`() {
        val state = ConsequenceState()
        state.fit(true)
        state.locate()
        assertEquals(ConsequencePhase.INPUT, state.phase)
        assertFalse(state.targetOpen)
        assertNull(state.step)
    }

    @Test
    fun `narration claims a step at most once until another step or a reset and respects every gate`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                val narration = ConsequenceNarration()
                var lastSpoken: ConsequenceStep? = null
                repeat(rnd.nextInt(1, 40)) { index ->
                    val step = randomStep(rnd)
                    val enabled = rnd.nextInt(4) != 0
                    val ready = rnd.nextInt(4) != 0
                    val screenReader = rnd.nextInt(4) == 0
                    val menu = rnd.nextInt(4) == 0
                    val label = "seed=$seed iteration=$iteration index=$index"
                    val claimed = narration.claim(step, enabled, ready, screenReader, menu)
                    val gatesOpen = enabled && ready && !screenReader && !menu
                    assertEquals(gatesOpen && step != lastSpoken, claimed, label)
                    if (claimed) {
                        lastSpoken = step
                        assertFalse(narration.claim(step, enabled, ready, screenReader, menu), "double claim: $label")
                    }
                    if (rnd.nextInt(10) == 0) {
                        narration.spokenStep = null
                        lastSpoken = null
                    }
                    assertEquals(lastSpoken, narration.spokenStep, label)
                }
            }
        }
    }

    @Test
    fun `runtime visibility follows suppress connect and disconnect events`() {
        for (seed in SEEDS) {
            val rnd = Random(seed)
            repeat(ITERATIONS) { iteration ->
                HelperRuntime.disconnect()
                HelperRuntime.suppress(false)
                var connected = false
                var suppressed = false
                var visibleAtSuppress = false
                var connectedDuringSuppress = false
                val log = mutableListOf<String>()
                repeat(rnd.nextInt(1, 40)) { index ->
                    val event = rnd.nextInt(6)
                    log += "e$event"
                    val label = "seed=$seed iteration=$iteration index=$index events=$log"
                    when (event) {
                        0 -> {
                            HelperRuntime.connect()
                            HelperRuntime.show = { HelperRuntime.visible = true }
                            HelperRuntime.hide = { HelperRuntime.visible = false }
                            connected = true
                            if (suppressed) connectedDuringSuppress = true
                        }

                        1 -> {
                            HelperRuntime.disconnect()
                            connected = false
                        }

                        2 -> {
                            val wasVisible = HelperRuntime.visible
                            HelperRuntime.suppress(true)
                            if (!suppressed) {
                                visibleAtSuppress = wasVisible
                                connectedDuringSuppress = false
                            }
                            suppressed = true
                            assertFalse(HelperRuntime.visible, "suppress must hide: $label")
                            val snapshot = HelperRuntime.visible to HelperRuntime.suppressed
                            HelperRuntime.suppress(true)
                            assertEquals(snapshot, HelperRuntime.visible to HelperRuntime.suppressed, "suppress idempotent: $label")
                        }

                        3 -> {
                            val wasSuppressed = suppressed
                            HelperRuntime.suppress(false)
                            suppressed = false
                            if (wasSuppressed && connected && (visibleAtSuppress || connectedDuringSuppress)) {
                                assertTrue(HelperRuntime.visible, "must restore after practice: $label")
                            } else if (wasSuppressed) {
                                assertFalse(HelperRuntime.visible, "must not show helper that was not visible: $label")
                            }
                            val after = HelperRuntime.visible
                            HelperRuntime.suppress(false)
                            assertEquals(after, HelperRuntime.visible, "unsuppress idempotent: $label")
                            visibleAtSuppress = false
                            connectedDuringSuppress = false
                        }

                        4 -> {
                            if (connected && !suppressed) HelperRuntime.show?.invoke()
                        }

                        else -> {
                            if (connected) HelperRuntime.hide?.invoke()
                        }
                    }
                    assertEquals(connected, HelperRuntime.connected, label)
                    assertEquals(suppressed, HelperRuntime.suppressed, label)
                    if (!connected) assertFalse(HelperRuntime.visible, "disconnected helper cannot be visible: $label")
                    if (suppressed) assertFalse(HelperRuntime.visible, "suppressed helper cannot be visible: $label")
                }
            }
        }
    }

    private companion object {
        val SEEDS = listOf(1L, 2L, 3L, 42L, 2024L, 99991L)
        const val ITERATIONS = 400
    }
}
