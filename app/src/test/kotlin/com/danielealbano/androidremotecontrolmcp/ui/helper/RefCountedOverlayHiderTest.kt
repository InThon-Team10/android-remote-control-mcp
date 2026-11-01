package com.danielealbano.androidremotecontrolmcp.ui.helper

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefCountedOverlayHiderTest {
    private val calls = mutableListOf<Boolean>()

    private fun TestScope.hider() = RefCountedOverlayHider(StandardTestDispatcher(testScheduler), 50) { calls.add(it) }

    @Test
    fun `hides while the block runs and restores afterwards`() =
        runTest {
            val result = hider().hiddenDuring { calls.toList() }

            assertEquals(listOf(true), result)
            assertEquals(listOf(true, false), calls)
        }

    @Test
    fun `overlapping calls restore only when the last one returns`() =
        runTest {
            val hider = hider()
            val first = async { hider.hiddenDuring { delay(500) } }
            runCurrent()
            val second = async { hider.hiddenDuring { delay(100) } }
            advanceTimeBy(300)
            runCurrent()

            assertEquals(listOf(true), calls)
            first.await()
            second.await()
            assertEquals(listOf(true, false), calls)
        }

    @Test
    fun `restores when the block throws`() =
        runTest {
            val hider = hider()

            val failure = runCatching { hider.hiddenDuring { error("boom") } }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals(listOf(true, false), calls)
        }

    @Test
    fun `restores when the caller is cancelled`() =
        runTest {
            val job = launch { hider().hiddenDuring { awaitCancellation() } }
            runCurrent()
            advanceTimeBy(100)

            job.cancelAndJoin()

            assertEquals(listOf(true, false), calls)
        }
}
