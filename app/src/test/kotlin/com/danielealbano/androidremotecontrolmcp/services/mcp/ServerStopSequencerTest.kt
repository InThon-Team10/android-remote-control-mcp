package com.danielealbano.androidremotecontrolmcp.services.mcp

import com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel.ArcTunnelRenewals
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("ServerStopSequencer")
class ServerStopSequencerTest {
    private val renewals = mockk<ArcTunnelRenewals>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val calls = CopyOnWriteArrayList<String>()
    private val sequencer = ServerStopSequencer(renewals, scope, dispatcher)
    private val idle = CompletableDeferred<Unit>()

    @BeforeEach
    fun setUp() {
        coEvery { renewals.awaitIdle() } coAnswers { idle.await() }
    }

    private fun stop() {
        sequencer.stop(
            teardown = { calls += TEARDOWN },
            showFinishing = { calls += FINISHING },
            stopService = { calls += STOP_SERVICE },
        )
    }

    @Test
    fun `nothing in flight stops the service at once`() {
        every { renewals.stop() } returns false

        stop()

        assertEquals(listOf(STOP_SERVICE), calls.toList())
    }

    @Test
    fun `an attempt in flight tears down now and stops the service once it is saved`() =
        scope.runTest {
            every { renewals.stop() } returns true

            stop()
            advanceUntilIdle()
            assertEquals(listOf(TEARDOWN, FINISHING), calls.toList())

            idle.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(TEARDOWN, FINISHING, STOP_SERVICE), calls.toList())
        }

    @Test
    fun `a start cancels the pending stop`() =
        scope.runTest {
            every { renewals.stop() } returns true
            stop()

            sequencer.cancelPendingStop()
            idle.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(TEARDOWN, FINISHING), calls.toList())
        }

    private companion object {
        const val TEARDOWN = "teardown"
        const val FINISHING = "finishing"
        const val STOP_SERVICE = "stopService"
    }
}
