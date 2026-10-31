package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BubbleOverlayHiderTest {
    private val controller = mockk<BubbleWindowController>(relaxed = true)

    private fun hiderOn(dispatcher: CoroutineDispatcher) = BubbleOverlayHider({ controller }, dispatcher, 10)

    @Test
    fun `windows are touch-through before the block and restored after`() =
        runTest {
            val hider = hiderOn(StandardTestDispatcher(testScheduler))

            val result =
                hider.hiddenDuring {
                    verify { controller.setTouchThrough(true) }
                    7
                }

            assertEquals(7, result)
            verifyOrder {
                controller.setTouchThrough(true)
                controller.setTouchThrough(false)
            }
        }

    @Test
    fun `overlapping blocks restore only when the last one ends`() =
        runTest {
            val hider = hiderOn(StandardTestDispatcher(testScheduler))
            val gate = CompletableDeferred<Unit>()
            val first = launch { hider.hiddenDuring { gate.await() } }
            val second = launch { hider.hiddenDuring { } }
            advanceUntilIdle()

            verify(exactly = 0) { controller.setTouchThrough(false) }
            gate.complete(Unit)
            first.join()
            second.join()

            verify(exactly = 1) { controller.setTouchThrough(true) }
            verify(exactly = 1) { controller.setTouchThrough(false) }
        }

    @Test
    fun `windows are restored when the block throws`() =
        runTest {
            val hider = hiderOn(StandardTestDispatcher(testScheduler))

            val failure = runCatching { hider.hiddenDuring { error("boom") } }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)

            verify { controller.setTouchThrough(false) }
        }

    @Test
    fun `windows are restored when the caller is cancelled`() =
        runTest {
            val hider = hiderOn(StandardTestDispatcher(testScheduler))
            val job = launch { hider.hiddenDuring { CompletableDeferred<Unit>().await() } }
            advanceUntilIdle()

            job.cancel()
            advanceUntilIdle()

            verify { controller.setTouchThrough(false) }
        }
}
