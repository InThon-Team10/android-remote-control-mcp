package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ArcTunnelIssuanceGate")
class ArcTunnelIssuanceGateTest {
    private val gate = ArcTunnelIssuanceGate()

    @Test
    fun `an open gate runs the attempt`() =
        runBlocking {
            assertEquals(7, gate.attempt { 7 })
            assertFalse(gate.stop())
        }

    @Test
    fun `a stopped gate refuses new attempts`() {
        gate.stop()

        val error = assertThrows<IllegalStateException> { runBlocking { gate.attempt { 1 } } }

        assertEquals(ArcTunnelIssuanceGate.STOPPED, error.message)
        assertTrue(gate.isStopped())
    }

    @Test
    fun `stop reports an attempt in flight and awaitIdle returns once it ends`() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val attempt =
                async {
                    gate.attempt {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            entered.await()

            assertTrue(gate.stop())
            val idle = async { gate.awaitIdle() }
            delay(SETTLE_MS)
            assertFalse(idle.isCompleted)

            release.complete(Unit)
            withTimeout(BUDGET_MS) { idle.await() }
            attempt.await()
        }

    @Test
    fun `resume allows attempts again`() =
        runBlocking {
            gate.stop()

            gate.resume()

            assertEquals(2, gate.attempt { 2 })
            assertFalse(gate.isStopped())
        }

    @Test
    fun `a failed attempt still leaves the gate idle`() =
        runBlocking {
            runCatching { gate.attempt { error("boom") } }

            withTimeout(BUDGET_MS) { gate.awaitIdle() }
            assertFalse(gate.stop())
        }

    private companion object {
        const val SETTLE_MS = 100L
        const val BUDGET_MS = 5_000L
    }
}
