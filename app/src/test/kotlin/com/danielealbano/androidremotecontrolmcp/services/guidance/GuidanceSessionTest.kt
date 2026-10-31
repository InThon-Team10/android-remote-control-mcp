package com.danielealbano.androidremotecontrolmcp.services.guidance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GuidanceSessionTest {
    private val session = GuidanceSession()
    private val target = HighlightBounds(10, 20, 100, 120)

    @Test
    fun `test input starts in analysis without assuming a target`() {
        val state = session.start()
        assertEquals("카카오톡 켜는법", state.goal)
        assertEquals(GuidancePhase.NEEDS_ANALYSIS, state.phase)
        assertNull(state.bounds)
    }

    @Test
    fun `highlight waits indefinitely for user and retry is idempotent`() {
        val revision = session.start().revision
        val first = session.propose(revision, "누르세요", target)
        assertEquals(GuidancePhase.WAITING_USER, first.phase)
        assertEquals(first, session.propose(revision, "누르세요", target))
        assertEquals(1, session.state.value.step)
        assertThrows(IllegalStateException::class.java) { session.complete(revision) }
        assertThrows(IllegalStateException::class.java) { session.propose(revision, "다른 안내", target) }
    }

    @Test
    fun `screen change clears target and requests analysis without declaring success`() {
        val revision = session.start().revision
        session.propose(revision, "누르세요", target)
        val changed = session.screenChanged()
        assertEquals(GuidancePhase.NEEDS_ANALYSIS, changed.phase)
        assertNull(changed.bounds)
        assertEquals(revision + 1, changed.revision)
        assertThrows(IllegalStateException::class.java) { session.propose(revision, "누르세요", target) }
        assertThrows(IllegalStateException::class.java) { session.complete(revision) }
    }

    @Test
    fun `multiple user steps reach completion only after explicit verification`() {
        session.start()
        repeat(3) {
            session.propose(session.state.value.revision, "누르세요", target)
            session.screenChanged()
        }
        assertEquals(3, session.state.value.step)
        assertEquals(GuidancePhase.COMPLETED, session.complete(session.state.value.revision).phase)
    }

    @Test
    fun `cancel and disconnect clear target and reject subsequent steps`() {
        val revision = session.start().revision
        session.propose(revision, "누르세요", target)
        assertNull(session.cancel().bounds)
        assertThrows(IllegalStateException::class.java) { session.propose(revision, "누르세요", target) }
        val restarted = session.start()
        session.propose(restarted.revision, "누르세요", target)
        assertNull(session.disconnect().bounds)
        assertEquals(GuidancePhase.DISCONNECTED, session.state.value.phase)
    }

    @Test
    fun `new session cannot reuse an old revision or overwrite an active goal`() {
        val first = session.start()
        assertThrows(IllegalStateException::class.java) { session.start("다른 목표") }
        session.cancel()
        session.start("다른 목표")
        assertThrows(IllegalStateException::class.java) { session.propose(first.revision, "누르세요", target) }
    }

    @Test
    fun `invalid goals instructions and rectangles are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { session.start(" ") }
        val revision = session.start().revision
        assertThrows(IllegalArgumentException::class.java) { session.propose(revision, "", target) }
        assertThrows(IllegalArgumentException::class.java) { HighlightBounds(10, 20, 10, 30) }
        assertThrows(IllegalArgumentException::class.java) { HighlightBounds(-1, 20, 10, 30) }
    }
}
