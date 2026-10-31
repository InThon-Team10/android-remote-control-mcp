package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Server retry_after cap in tunnel retries")
class ArcTunnelRetryAfterCapTest {
    private val cap = ArcTunnelBackoff.MAX_MS

    private fun refusal(retryAfterSec: Long) = ArcTunnelApiException("busy", true, retryAfterSec, 503)

    @ParameterizedTest
    @ValueSource(longs = [Long.MAX_VALUE, Long.MAX_VALUE / 1000 + 1, 1L shl 53, 17L])
    fun `huge retry_after is capped in issue and enroll retries`(sec: Long) {
        assertEquals(cap, ArcTunnelIssueRetry.waitMs(refusal(sec), 0))
        assertEquals(cap, ArcTunnelEnrollRetry.waitMs(refusal(sec)))
        assertEquals(cap, refusal(sec).retryAfterMs)
    }

    @Test
    fun `retry_after equal to the cap is unchanged`() {
        assertEquals(cap, ArcTunnelIssueRetry.waitMs(refusal(cap / 1000), 0))
        assertEquals(cap, ArcTunnelEnrollRetry.waitMs(refusal(cap / 1000)))
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, -1L, Long.MIN_VALUE])
    fun `zero and negative retry_after keep the fallback waits`(sec: Long) {
        assertEquals(5_000L, ArcTunnelIssueRetry.waitMs(refusal(sec), 0))
        assertEquals(10_000L, ArcTunnelIssueRetry.waitMs(refusal(sec), 1))
        assertEquals(ArcTunnelEnrollRetry.REFUSAL_WAIT_MS, ArcTunnelEnrollRetry.waitMs(refusal(sec)))
    }

    @Test
    fun `normal retry_after values are unchanged`() {
        assertEquals(7_000L, ArcTunnelIssueRetry.waitMs(refusal(7), 2))
        assertEquals(7_000L, ArcTunnelEnrollRetry.waitMs(refusal(7)))
        assertEquals(1_000L, ArcTunnelIssueRetry.waitMs(refusal(1), 0))
        assertEquals(1_000L, ArcTunnelEnrollRetry.waitMs(refusal(1)))
    }
}
