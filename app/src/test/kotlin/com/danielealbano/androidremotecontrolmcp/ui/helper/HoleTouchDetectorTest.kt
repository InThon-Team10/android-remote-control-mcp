package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HoleTouchDetectorTest {
    private val detector = HoleTouchDetector()

    @BeforeEach
    fun setUp() {
        detector.reset(setOf(0, 1, 2, 3))
    }

    private fun outsideAll(
        eventTime: Long,
        except: Int? = null,
    ) = (0..3).filter { it != except }.forEach { detector.record(it, WindowTouch.OUTSIDE, eventTime) }

    @Test
    fun `touch is in the hole when every blocker saw it outside and none received it`() {
        outsideAll(100)

        assertTrue(detector.decide(100))
    }

    @Test
    fun `touch on a blocker is not a hole touch`() {
        detector.record(1, WindowTouch.DOWN, 100)
        outsideAll(100, except = 1)

        assertFalse(detector.decide(100))
    }

    @Test
    fun `touch missing from one blocker is not a hole touch`() {
        outsideAll(100, except = 2)

        assertFalse(detector.decide(100))
    }

    @Test
    fun `only the first outside touch of an event time asks for a decision`() {
        assertTrue(detector.record(0, WindowTouch.OUTSIDE, 100))
        assertFalse(detector.record(1, WindowTouch.OUTSIDE, 100))
        assertFalse(detector.record(0, WindowTouch.DOWN, 100))
        assertTrue(detector.record(0, WindowTouch.OUTSIDE, 200))
    }

    @Test
    fun `touches with different event times are decided independently`() {
        outsideAll(100)
        detector.record(0, WindowTouch.DOWN, 200)
        outsideAll(200, except = 0)

        assertTrue(detector.decide(100))
        assertFalse(detector.decide(200))
    }

    @Test
    fun `decision is made once and then forgotten`() {
        outsideAll(100)

        assertTrue(detector.decide(100))
        assertFalse(detector.decide(100))
    }

    @Test
    fun `nothing is detected without blockers`() {
        detector.reset(emptySet())

        assertFalse(detector.record(0, WindowTouch.OUTSIDE, 100))
        assertFalse(detector.decide(100))
    }

    @Test
    fun `reset forgets pending touches`() {
        outsideAll(100)
        detector.reset(setOf(0, 1, 2, 3))

        assertFalse(detector.decide(100))
    }

    @Test
    fun `old pending touches are dropped`() {
        outsideAll(1)
        (2L..20L).forEach { detector.record(0, WindowTouch.OUTSIDE, it) }

        assertFalse(detector.decide(1))
    }
}
