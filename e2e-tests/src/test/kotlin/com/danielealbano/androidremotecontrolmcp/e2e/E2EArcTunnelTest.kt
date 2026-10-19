package com.danielealbano.androidremotecontrolmcp.e2e

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * E2E: the free E2E tunnel against a local tunnel server stack ([ArcTunnelStack]). The redroid phone enrolls
 * (attestation optional on the server: redroid has a software KeyMint), the MCP server starts with the
 * tunnel, and the JVM reaches `/health` and `tools/list` THROUGH the tunnel with the Pebble root as trust
 * anchor. The shared container is restored (tunnel off, server restarted, client refreshed) afterwards.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2EArcTunnelTest {
    private lateinit var stack: ArcTunnelStack
    private lateinit var status: E2EArcTunnelStatus
    private val baseUrl = SharedAndroidContainer.mcpServerUrl

    @BeforeAll
    fun startStackAndTunnel() {
        SharedAndroidContainer.ensureAccessibilityService()
        val gateway = AndroidContainerSetup.containerGatewayIp()
        stack = ArcTunnelStack(dialBackAdvertise = "$gateway:${ArcTunnelStack.EDGE_HOST_PORT}")
        stack.start()

        AndroidContainerSetup.stopMcpServer(baseUrl)
        AndroidContainerSetup.configureArcTunnel(
            ArcTunnelE2ESettings(
                tunnelEnabled = true,
                provider = ArcTunnelE2ESettings.ARC_PROVIDER,
                baseAddress = ArcTunnelStack.BASE_ADDRESS,
                ctCheckEnabled = false,
                edgeOverride = stack.edgeOverride(gateway),
                trustedCaPem = stack.issuingRootPem,
            ),
        )
        AndroidContainerSetup.enrollArcTunnel()
        AndroidContainerSetup.awaitArcTunnel(timeoutMs = ENROLL_TIMEOUT_MS) { it.enrolled && it.enrollment == "Idle" }
        AndroidContainerSetup.startMcpServer()
        AndroidContainerSetup.waitForServerReady(baseUrl)
        status = AndroidContainerSetup.awaitArcTunnel(timeoutMs = CONNECT_TIMEOUT_MS) { it.tunnel == "Connected" }
    }

    @AfterAll
    fun restoreSharedContainer() {
        try {
            AndroidContainerSetup.stopMcpServer(baseUrl)
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
    fun `the public URL is the enrolled name under the tunnel domain`() {
        val url = requireNotNull(status.url) { "no URL in $status" }
        assertTrue(url.startsWith("https://") && url.endsWith(".${ArcTunnelStack.TUNNEL_DOMAIN}"), url)
        assertEquals(url, status.tunnelUrl)
    }

    @Test
    fun `health is served through the tunnel with a Pebble-issued certificate`() {
        val response = ArcTunnelPublicClient.health(stack, fqdn())
        assertTrue(response.startsWith("HTTP/1.1 200"), response)
        assertTrue(response.contains("\"status\""), response)
    }

    @Test
    fun `tools are listed through the tunnel`() =
        runBlocking {
            val client = ArcTunnelPublicClient.stackMcpClient(stack, fqdn(), AndroidContainerSetup.E2E_BEARER_TOKEN)
            try {
                client.connect()
                val tools = client.listTools()
                assertTrue(tools.tools.any { it.name == "${AndroidContainerSetup.TOOL_NAME_PREFIX}press_home" })
            } finally {
                client.close()
            }
        }

    @Test
    fun `a second connection reuses the bound control stream`() {
        val first = ArcTunnelPublicClient.health(stack, fqdn())
        val second = ArcTunnelPublicClient.health(stack, fqdn())
        assertTrue(first.startsWith("HTTP/1.1 200") && second.startsWith("HTTP/1.1 200"), "$first\n$second")
        assertEquals("Connected", AndroidContainerSetup.queryArcTunnel().tunnel)
    }

    private fun fqdn(): String = requireNotNull(status.url).removePrefix("https://")

    companion object {
        private const val ENROLL_TIMEOUT_MS = 180_000L
        private const val CONNECT_TIMEOUT_MS = 120_000L
    }
}
