package com.danielealbano.androidremotecontrolmcp.e2e

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E: free E2E tunnel enrollment and certificate issuance against a local tunnel server stack
 * ([ArcTunnelStack]). Redroid has no hardware keystore (its AndroidKeyStore is a software KeyMint), so the server
 * runs with attestation optional and the tunnel itself is NOT started here: the full path through the tunnel is
 * covered on real hardware by [E2EArcTunnelDeviceTest]. The shared container is restored (tunnel settings reset,
 * server restarted, client refreshed) afterwards.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2EArcTunnelTest {
    private lateinit var stack: ArcTunnelStack
    private lateinit var status: E2EArcTunnelStatus
    private val baseUrl = SharedAndroidContainer.mcpServerUrl

    @BeforeAll
    fun startStackAndEnroll() {
        SharedAndroidContainer.ensureAccessibilityService()
        val gateway = AndroidContainerSetup.containerGatewayIp()
        stack = ArcTunnelStack(dialBackAdvertise = "$gateway:${ArcTunnelStack.EDGE_HOST_PORT}")
        stack.start()

        // Enrollment is refused while the MCP server runs.
        AndroidContainerSetup.stopMcpServer(baseUrl)
        AndroidContainerSetup.configureArcTunnel(
            ArcTunnelE2ESettings(
                tunnelEnabled = false,
                provider = ArcTunnelE2ESettings.ARC_PROVIDER,
                baseAddress = ArcTunnelStack.BASE_ADDRESS,
                ctCheckEnabled = false,
                edgeOverride = stack.edgeOverride(gateway),
                trustedCaPem = stack.issuingRootPem,
            ),
        )
        AndroidContainerSetup.enrollArcTunnel()
        status =
            AndroidContainerSetup.awaitArcTunnel(timeoutMs = ENROLL_TIMEOUT_MS) {
                it.enrolled && it.enrollment == "Idle"
            }
    }

    @AfterAll
    fun restoreSharedContainer() {
        try {
            AndroidContainerSetup.configureArcTunnel(
                ArcTunnelE2ESettings(
                    tunnelEnabled = false,
                    provider = ArcTunnelE2ESettings.CLOUDFLARE_PROVIDER,
                    baseAddress = ArcTunnelStack.BASE_ADDRESS,
                    ctCheckEnabled = true,
                ),
            )
            AndroidContainerSetup.startMcpServer()
            AndroidContainerSetup.waitForServerReady(baseUrl)
            SharedAndroidContainer.refreshMcpClient()
        } finally {
            if (::stack.isInitialized) stack.close()
        }
    }

    @Test
    fun `enrollment assigns a name under the tunnel domain`() {
        val url = requireNotNull(status.url) { "no URL in $status" }
        assertTrue(url.startsWith("https://") && url.endsWith(".${ArcTunnelStack.TUNNEL_DOMAIN}"), url)
        assertEquals(ArcTunnelStack.BASE_ADDRESS, status.baseAddress)
    }

    @Test
    fun `issuance saves the public certificate and leaves the enrollment idle`() {
        // `enrolled` is true only once the issued public chain is saved (a name alone does not count).
        assertTrue(status.enrolled, "$status")
        assertEquals("Idle", status.enrollment)
    }

    companion object {
        private const val ENROLL_TIMEOUT_MS = 180_000L
    }
}
