package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@DisplayName("ArcTunnelRenewalsImpl")
class ArcTunnelRenewalsImplTest {
    private val enrollment = mockk<ArcTunnelEnrollment>(relaxed = true)
    private val identity = mockk<ArcTunnelIdentity>(relaxed = true)
    private val serverLog = RecordingServerLogRepository()
    private val renewals = ArcTunnelRenewalsImpl(enrollment, serverLog)
    private val invoked = CompletableDeferred<Unit>()

    /** The mocked issuance signals [invoked], then runs [body] with the gate it was given. */
    private fun issuance(body: suspend (ArcTunnelIssuanceGate) -> Unit) {
        coEvery { enrollment.issueAndSave(any(), any(), any()) } coAnswers {
            invoked.complete(Unit)
            body(thirdArg())
        }
    }

    private suspend fun awaitInvoked() {
        withTimeout(BUDGET_MS) { invoked.await() }
    }

    @Test
    fun `a start renewal shows Renewing then Idle and logs it`() =
        runBlocking {
            val seen = AtomicReference<ArcTunnelRenewalState>()
            issuance { seen.set(renewals.state.value) }

            renewals.renewForStart(identity)

            assertEquals(ArcTunnelRenewalState.Renewing, seen.get())
            assertEquals(ArcTunnelRenewalState.Idle, renewals.state.value)
            assertTrue(serverLog.entries.any { it.message.contains("renewed at start") })
        }

    @Test
    fun `a failed start renewal shows the reason and throws it`() =
        runBlocking {
            val failure = ArcTunnelApiException("acme_rate_limited", true, 300, 503)
            issuance { throw failure }

            val error = runCatching { renewals.renewForStart(identity) }.exceptionOrNull()

            assertEquals(failure, error)
            assertEquals(ArcTunnelRenewalState.Failed(failure.message.orEmpty()), renewals.state.value)
        }

    @Test
    fun `cancel aborts the start renewal at once`() =
        runBlocking {
            issuance { awaitCancellation() }
            val start = async(Dispatchers.IO) { runCatching { renewals.renewForStart(identity) }.exceptionOrNull() }
            awaitInvoked()

            renewals.cancel()

            val error = withTimeout(BUDGET_MS) { start.await() }
            assertEquals(ArcTunnelRenewalsImpl.CANCELLED, error?.message)
            assertEquals(ArcTunnelRenewalState.Idle, renewals.state.value)
        }

    @Test
    fun `cancelling the caller does not cancel the issuance`() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val completed = AtomicBoolean(false)
            issuance {
                release.await()
                completed.set(true)
            }
            val caller = launch(Dispatchers.IO) { renewals.renewForStart(identity) }
            awaitInvoked()

            caller.cancelAndJoin()
            release.complete(Unit)

            withTimeout(BUDGET_MS) { renewals.awaitIdle() }
            assertTrue(completed.get())
            assertEquals(ArcTunnelRenewalState.Idle, renewals.state.value)
        }

    @Test
    fun `stop with no attempt in flight cancels the pending renewals at once`() =
        runBlocking {
            issuance { awaitCancellation() }
            val routine = async(Dispatchers.IO) { runCatching { renewals.renew(identity, "0a01") } }
            awaitInvoked()

            assertFalse(renewals.stop())

            withTimeout(BUDGET_MS) { renewals.awaitIdle() }
            assertTrue(withTimeout(BUDGET_MS) { routine.await() }.isFailure)
        }

    @Test
    fun `stop with an attempt in flight lets it finish and refuses new ones`() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            issuance { gate ->
                gate.attempt {
                    entered.complete(Unit)
                    release.await()
                }
            }
            val routine = async(Dispatchers.IO) { renewals.renew(identity, "0a01") }
            withTimeout(BUDGET_MS) { entered.await() }

            assertTrue(renewals.stop())
            val idle = async { renewals.awaitIdle() }
            delay(SETTLE_MS)
            assertFalse(idle.isCompleted)

            release.complete(Unit)
            withTimeout(BUDGET_MS) { idle.await() }
            withTimeout(BUDGET_MS) { routine.await() }
            val refused = runCatching { renewals.renew(identity, "0a02") }.exceptionOrNull()
            assertEquals(ArcTunnelIssuanceGate.STOPPED, refused?.message)
        }

    @Test
    fun `resume reopens the gate after a stop`() =
        runBlocking {
            issuance { gate -> gate.attempt { } }
            renewals.stop()

            renewals.resume()

            renewals.renew(identity, "0a01")
        }

    @Test
    fun `retry releases awaitRetry`() =
        runBlocking {
            val waiting = async(Dispatchers.IO) { renewals.awaitRetry() }

            renewals.retry()

            withTimeout(BUDGET_MS) { waiting.await() }
        }

    @Test
    fun `stop clears a failed state`() =
        runBlocking {
            issuance { throw ArcTunnelApiException("refused", false, 0, 403) }
            runCatching { renewals.renewForStart(identity) }

            renewals.stop()

            assertEquals(ArcTunnelRenewalState.Idle, renewals.state.value)
        }

    @Test
    fun `a renewal halted by a stop is not shown as failed`() =
        runBlocking {
            issuance {
                renewals.stop()
                throw IllegalStateException(ArcTunnelIssuanceGate.STOPPED)
            }

            runCatching { renewals.renewForStart(identity) }

            assertEquals(ArcTunnelRenewalState.Idle, renewals.state.value)
        }

    private companion object {
        const val BUDGET_MS = 5_000L
        const val SETTLE_MS = 100L
    }
}
