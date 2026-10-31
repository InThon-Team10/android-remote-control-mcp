package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.CertificateManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyStore

class AssistantMcpConnectionImplTest {
    @TempDir
    lateinit var tempDir: File

    private val servers = mutableListOf<AssistantMcpTestServer>()
    private val settings = mockk<SettingsRepository>()
    private var certificateManager = mockk<CertificateManager>()
    private val connection by lazy { AssistantMcpConnectionImpl(settings, certificateManager) }

    @AfterEach
    fun tearDown() {
        runBlocking { connection.close() }
        servers.forEach { it.close() }
    }

    private fun server(
        token: String = AssistantMcpTestServer.TOKEN,
        keyStore: KeyStore? = null,
        password: CharArray = CharArray(0),
        port: Int = 0,
    ) = AssistantMcpTestServer(token, keyStore, password, port).also { servers += it }

    private fun config(
        port: Int,
        token: String = AssistantMcpTestServer.TOKEN,
        enabled: Boolean = true,
        https: Boolean = false,
    ) = ServerConfig(port = port, bearerToken = token, bearerTokenEnabled = enabled, httpsEnabled = https)

    private fun use(config: ServerConfig) {
        coEvery { settings.getServerConfig() } returns config
    }

    private fun realManager(dir: File): CertificateManager {
        val context = mockk<Context>(relaxed = true) { every { filesDir } returns dir }
        return CertificateManager(context)
    }

    private fun error(result: Result<*>): AssistantError = (result.exceptionOrNull() as AssistantException).error

    private fun ping() = runBlocking { connection.callTool("ping", JsonObject(emptyMap())) }

    @Test
    fun `connect succeeds against real server`() {
        use(config(server().port))
        assertTrue(runBlocking { connection.connect() }.isSuccess)
    }

    @Test
    fun `connect is idempotent`() {
        use(config(server().port))
        assertTrue(runBlocking { connection.connect() }.isSuccess)
        assertTrue(runBlocking { connection.connect() }.isSuccess)
        assertTrue(ping().isSuccess)
    }

    @Test
    fun `connect fails with BearerDisabled when bearer disabled`() {
        use(config(server().port, enabled = false))
        assertEquals(AssistantError.BearerDisabled, error(runBlocking { connection.connect() }))
    }

    @Test
    fun `connect fails with BearerDisabled when token blank`() {
        use(config(server().port, token = " "))
        assertEquals(AssistantError.BearerDisabled, error(runBlocking { connection.connect() }))
    }

    @Test
    fun `connect fails with McpUnavailable on wrong token`() {
        use(config(server(token = "other").port))
        assertTrue(error(runBlocking { connection.connect() }) is AssistantError.McpUnavailable)
    }

    @Test
    fun `connect fails with McpUnavailable when nothing listens`() {
        val closedPort = server().also { it.close() }.port
        use(config(closedPort))
        val result = runBlocking { withTimeout(NO_HANG_MS) { connection.connect() } }
        assertTrue(error(result) is AssistantError.McpUnavailable)
    }

    @Test
    fun `listTools returns server tools`() {
        use(config(server().port))
        val names = runBlocking { connection.listTools() }.getOrThrow().map { it.name }
        assertTrue(names.containsAll(listOf("ping", "fail")))
    }

    @Test
    fun `callTool returns text result`() {
        use(config(server().port))
        val result = ping().getOrThrow()
        assertNotEquals(true, result.isError)
        assertEquals("pong", (result.content[0] as TextContent).text)
    }

    @Test
    fun `callTool surfaces tool error as result not failure`() {
        use(config(server().port))
        val result = runBlocking { connection.callTool("fail", JsonObject(emptyMap())) }
        assertEquals(true, result.getOrThrow().isError)
    }

    @Test
    fun `callTool without prior connect connects lazily`() {
        use(config(server().port))
        assertTrue(ping().isSuccess)
    }

    @Test
    fun `reconnects after server restart`() {
        val first = server()
        use(config(first.port))
        assertTrue(ping().isSuccess)
        first.close()
        assertTrue(error(ping()) is AssistantError.McpUnavailable)
        server(port = first.port)
        assertTrue(ping().isSuccess)
    }

    @Test
    fun `close then callTool reconnects`() {
        use(config(server().port))
        assertTrue(ping().isSuccess)
        runBlocking {
            connection.close()
            connection.close()
        }
        assertTrue(ping().isSuccess)
    }

    @Test
    fun `cancelled callTool propagates cancellation`() {
        use(config(server().port))
        runBlocking {
            val call = async(start = CoroutineStart.UNDISPATCHED) { connection.callTool("ping", JsonObject(mapOf())) }
            call.cancel()
            val outcome = runCatching { call.await() }
            assertTrue(outcome.exceptionOrNull() is CancellationException)
            withTimeout(NO_HANG_MS) { connection.close() }
        }
    }

    @Test
    fun `https connects when pinned to server certificate`() {
        val manager = realManager(tempDir)
        val keyStore = manager.getOrCreateKeyStore(ServerConfig())
        certificateManager = manager
        use(config(server(keyStore = keyStore, password = manager.getKeyStorePassword()).port, https = true))
        assertTrue(ping().isSuccess)
    }

    @Test
    fun `https rejects a server presenting another certificate`() {
        val clientManager = realManager(File(tempDir, "a").also { it.mkdirs() })
        val serverManager = realManager(File(tempDir, "b").also { it.mkdirs() })
        clientManager.getOrCreateKeyStore(ServerConfig())
        val serverKeyStore = serverManager.getOrCreateKeyStore(ServerConfig())
        certificateManager = clientManager
        val port = server(keyStore = serverKeyStore, password = serverManager.getKeyStorePassword()).port
        use(config(port, https = true))
        assertTrue(error(ping()) is AssistantError.McpUnavailable)
    }

    @Test
    fun `https config with plain server fails`() {
        val manager = realManager(tempDir)
        certificateManager = manager
        use(config(server().port, https = true))
        assertTrue(error(ping()) is AssistantError.McpUnavailable)
    }

    @Test
    fun `connect fails with McpUnavailable when certificate missing`() {
        every { certificateManager.getOrCreateKeyStore(any()) } returns
            KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        use(config(server().port, https = true))
        assertEquals(
            AssistantError.McpUnavailable("Server certificate not found"),
            error(runBlocking { connection.connect() }),
        )
    }

    private companion object {
        const val NO_HANG_MS = 15_000L
    }
}
