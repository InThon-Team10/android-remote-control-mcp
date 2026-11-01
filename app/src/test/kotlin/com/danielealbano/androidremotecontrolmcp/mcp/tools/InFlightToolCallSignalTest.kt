package com.danielealbano.androidremotecontrolmcp.mcp.tools

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("InFlightToolCallSignal")
class InFlightToolCallSignalTest {
    private val signal = InFlightToolCallSignal()

    @Test
    fun `inactive by default`() {
        assertFalse(signal.active.value)
    }

    @Test
    fun `started activates and finished deactivates`() {
        signal.onToolCallStarted("tap")
        assertTrue(signal.active.value)
        signal.onToolCallFinished("tap")
        assertFalse(signal.active.value)
    }

    @Test
    fun `overlapping calls stay active until all finish`() {
        signal.onToolCallStarted("a")
        signal.onToolCallStarted("b")
        signal.onToolCallFinished("a")
        assertTrue(signal.active.value)
        signal.onToolCallFinished("b")
        assertFalse(signal.active.value)
    }

    @Test
    fun `finish without start is ignored`() {
        signal.onToolCallFinished("a")
        assertFalse(signal.active.value)
        signal.onToolCallStarted("a")
        assertTrue(signal.active.value)
    }

    @Test
    fun `extra finish after balanced calls does not leave a negative count`() {
        signal.onToolCallStarted("a")
        signal.onToolCallFinished("a")
        signal.onToolCallFinished("a")
        signal.onToolCallStarted("a")
        assertTrue(signal.active.value)
        signal.onToolCallFinished("a")
        assertFalse(signal.active.value)
    }

    @Test
    fun `active flow emits transitions`() =
        runTest {
            signal.active.test {
                assertFalse(awaitItem())
                signal.onToolCallStarted("a")
                assertTrue(awaitItem())
                signal.onToolCallFinished("a")
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `concurrent start and finish pairs end inactive`() =
        runTest {
            (1..1000)
                .map {
                    async(Dispatchers.Default) {
                        signal.onToolCallStarted("a")
                        signal.onToolCallFinished("a")
                    }
                }.awaitAll()
            assertFalse(signal.active.value)
        }
}
