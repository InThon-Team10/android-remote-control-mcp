package com.danielealbano.androidremotecontrolmcp.services.guidance

import app.cash.turbine.test
import com.danielealbano.androidremotecontrolmcp.services.accessibility.BoundsData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GuidanceControllerImpl")
class GuidanceControllerImplTest {
    private fun target(
        kind: GuidanceKind = GuidanceKind.TAP,
        instruction: String = "Tap here",
    ) = GuidanceTarget(BoundsData(10, 20, 110, 120), kind, instruction)

    private fun TestScope.controller(attached: Boolean = true) =
        GuidanceControllerImpl(backgroundScope) { testScheduler.currentTime }
            .also { it.setRendererAttached(attached) }

    private fun TestScope.guide(
        controller: GuidanceController,
        target: GuidanceTarget,
    ): Deferred<GuidanceOutcome> = async { controller.guide(target) }.also { runCurrent() }

    /** Lets the wait run for [ms] virtual milliseconds. */
    private suspend fun TestScope.elapse(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `guide is UNAVAILABLE at once without a renderer and changes no state`() =
        runTest {
            val c = controller(attached = false)

            assertEquals(GuidanceOutcome.UNAVAILABLE, c.guide(target()))
            assertNull(c.state.value)
        }

    @Test
    fun `guide publishes the state while waiting and clears it when done`() =
        runTest {
            val c = controller()
            c.state.test {
                assertNull(awaitItem())
                val outcome = guide(c, target())
                assertEquals(target(), awaitItem()?.target)
                c.onUserActivity(UserActivity.CLICKED)
                elapse(300)
                assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
                assertNull(awaitItem())
            }
        }

    @Test
    fun `tap completes 300 ms after a click and not before`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            c.onUserActivity(UserActivity.CLICKED)
            elapse(299)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `tap completes after the target was touched`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            c.onUserActivity(UserActivity.TARGET_TOUCHED)
            elapse(300)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `tap ignores a long click and text changes`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            c.onUserActivity(UserActivity.LONG_CLICKED)
            c.onUserActivity(UserActivity.TEXT_CHANGED)
            c.onUserActivity(UserActivity.SCROLLED)
            elapse(5_000)

            assertFalse(outcome.isCompleted)
            c.cancel()
            assertEquals(GuidanceOutcome.CANCELLED, outcome.await())
        }

    @Test
    fun `double tap waits 800 ms so the second tap can land`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.DOUBLE_TAP))

            c.onUserActivity(UserActivity.CLICKED)
            elapse(799)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `long press completes at once on a long click`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.LONG_PRESS))

            c.onUserActivity(UserActivity.LONG_CLICKED)
            runCurrent()

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `long press completes 1500 ms after a plain click or touch`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.LONG_PRESS))

            c.onUserActivity(UserActivity.TARGET_TOUCHED)
            elapse(1_499)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `type restarts a 2500 ms quiet timer on each text change`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.TYPE))

            c.onUserActivity(UserActivity.TEXT_CHANGED)
            elapse(2_000)
            c.onUserActivity(UserActivity.TEXT_CHANGED)
            elapse(2_499)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `type is not completed by clicks that only focus the field`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.TYPE))

            c.onUserActivity(UserActivity.CLICKED)
            c.onUserActivity(UserActivity.TARGET_TOUCHED)
            elapse(10_000)

            assertFalse(outcome.isCompleted)
            c.cancel()
            outcome.await()
        }

    @Test
    fun `scroll completes after 600 ms without further scrolls`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.SCROLL))

            c.onUserActivity(UserActivity.SCROLLED)
            elapse(500)
            c.onUserActivity(UserActivity.SCROLLED)
            elapse(599)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `scroll or swipe completes 1200 ms after a touch in the highlighted area`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.SCROLL))

            c.onUserActivity(UserActivity.TARGET_TOUCHED)
            elapse(1_199)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `a window change is ignored in the first 700 ms of a new step`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            elapse(699)
            c.onUserActivity(UserActivity.WINDOW_CHANGED)
            runCurrent()

            assertFalse(outcome.isCompleted)
            c.cancel()
            outcome.await()
        }

    @Test
    fun `a window change after 700 ms ends every kind with SCREEN_CHANGED`() =
        runTest {
            val c = controller()
            for (kind in GuidanceKind.entries) {
                val outcome = guide(c, target(kind))
                elapse(700)

                c.onUserActivity(UserActivity.WINDOW_CHANGED)
                runCurrent()

                assertEquals(GuidanceOutcome.SCREEN_CHANGED, outcome.await(), kind.name)
                assertNull(c.state.value)
            }
        }

    @Test
    fun `a window change right after the user acted is still USER_ACTED`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            c.onUserActivity(UserActivity.CLICKED)
            c.onUserActivity(UserActivity.WINDOW_CHANGED)
            runCurrent()

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
        }

    @Test
    fun `without activity the wait times out after 45 s and the state lingers`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS - 1)
            assertFalse(outcome.isCompleted)
            elapse(1)

            assertEquals(GuidanceOutcome.TIMED_OUT, outcome.await())
            assertEquals(target(), c.state.value?.target)
        }

    @Test
    fun `at the cap after qualifying activity the outcome is USER_ACTED`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target(GuidanceKind.TYPE))

            repeat(20) {
                c.onUserActivity(UserActivity.TEXT_CHANGED)
                elapse(2_400)
            }

            assertEquals(GuidanceOutcome.USER_ACTED, outcome.await())
            assertNull(c.state.value)
        }

    @Test
    fun `a lingering state expires after 120 s`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            outcome.await()

            elapse(GuidanceControllerImpl.LINGER_MS - 1)
            assertTrue(c.state.value != null)
            elapse(1)

            assertNull(c.state.value)
        }

    @Test
    fun `user activity clears a lingering state and the same target is then USER_ACTED at once`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            first.await()

            c.onUserActivity(UserActivity.CLICKED)
            assertNull(c.state.value)

            assertEquals(GuidanceOutcome.USER_ACTED, c.guide(target()))
            assertNull(c.state.value)
        }

    @Test
    fun `the acted memory is used once and a different target forgets it`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            first.await()
            c.onUserActivity(UserActivity.CLICKED)

            val other = guide(c, target(instruction = "Other"))
            c.cancel()
            other.await()
            val again = guide(c, target())

            assertFalse(again.isCompleted)
            c.cancel()
            again.await()
        }

    @Test
    fun `a lingering state is cleared by cancel and by renderer detach`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            first.await()
            c.cancel()
            assertNull(c.state.value)

            val second = guide(c, target())
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            second.await()
            c.setRendererAttached(false)

            assertNull(c.state.value)
        }

    @Test
    fun `a different guide replaces a lingering state with a new id`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            val firstId = c.state.value?.id
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            first.await()

            val second = guide(c, target(instruction = "Other"))

            assertNotEquals(firstId, c.state.value?.id)
            assertEquals(
                "Other",
                c.state.value
                    ?.target
                    ?.instruction,
            )
            c.cancel()
            second.await()
        }

    @Test
    fun `the same target keeps the state id and supersedes the running wait`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            val id = c.state.value?.id

            val second = guide(c, target())

            assertEquals(GuidanceOutcome.SUPERSEDED, first.await())
            assertEquals(id, c.state.value?.id)
            c.cancel()
            second.await()
        }

    @Test
    fun `a same target after a timeout reuses the lingering id and does not restart the window grace`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            val id = c.state.value?.id
            elapse(GuidanceControllerImpl.GUIDANCE_WAIT_MS)
            first.await()

            val second = guide(c, target())
            assertEquals(id, c.state.value?.id)
            c.onUserActivity(UserActivity.WINDOW_CHANGED)
            runCurrent()

            assertEquals(GuidanceOutcome.SCREEN_CHANGED, second.await())
        }

    @Test
    fun `a different target gets a larger id and supersedes the running wait`() =
        runTest {
            val c = controller()
            val first = guide(c, target())
            val id = requireNotNull(c.state.value).id

            val second = guide(c, target(instruction = "Other"))

            assertEquals(GuidanceOutcome.SUPERSEDED, first.await())
            assertTrue(requireNotNull(c.state.value).id > id)
            c.cancel()
            second.await()
        }

    @Test
    fun `cancel ends the wait with CANCELLED and clears the state`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            c.cancel()

            assertEquals(GuidanceOutcome.CANCELLED, outcome.await())
            assertNull(c.state.value)
        }

    @Test
    fun `detaching the renderer makes the waiting step UNAVAILABLE and clears the state`() =
        runTest {
            val c = controller()
            val outcome = guide(c, target())

            c.setRendererAttached(false)

            assertEquals(GuidanceOutcome.UNAVAILABLE, outcome.await())
            assertNull(c.state.value)
            assertEquals(GuidanceOutcome.UNAVAILABLE, c.guide(target()))
        }

    @Test
    fun `cancelling the caller clears the state and rethrows`() =
        runTest {
            val c = controller()
            val started = CompletableDeferred<Unit>()
            val job =
                async {
                    started.complete(Unit)
                    c.guide(target())
                }
            runCurrent()
            started.await()
            assertTrue(c.state.value != null)

            job.cancelAndJoin()

            assertTrue(job.isCancelled)
            assertNull(c.state.value)
        }

    @Test
    fun `activity with no step shown is ignored`() =
        runTest {
            val c = controller()

            c.onUserActivity(UserActivity.CLICKED)
            val outcome = guide(c, target())
            elapse(1_000)

            assertFalse(outcome.isCompleted)
            c.cancel()
            outcome.await()
        }
}
