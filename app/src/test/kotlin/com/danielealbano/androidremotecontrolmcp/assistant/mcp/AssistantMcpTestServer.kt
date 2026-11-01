package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import com.danielealbano.androidremotecontrolmcp.mcp.CertificateManager
import com.danielealbano.androidremotecontrolmcp.mcp.InFlightToolCalls
import com.danielealbano.androidremotecontrolmcp.mcp.installMcpBasePlugins
import com.danielealbano.androidremotecontrolmcp.mcp.installMcpStatelessTransport
import com.danielealbano.androidremotecontrolmcp.mcp.tools.LoggedToolRegistrar
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.runBlocking
import java.security.KeyStore

/**
 * Real Netty MCP server on 127.0.0.1 (tools `ping` -> "pong", `fail` -> isError) with the production base plugins
 * and stateless transport. Plain HTTP when [keyStore] is null, otherwise HTTPS serving [keyStore]'s `mcp-server` key.
 */
internal class AssistantMcpTestServer(
    private val token: String = TOKEN,
    private val keyStore: KeyStore? = null,
    private val keyStorePassword: CharArray = CharArray(0),
    requestedPort: Int = 0,
) : AutoCloseable {
    private val sdk =
        Server(
            Implementation(name = "test", version = "1"),
            ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        )
    private val server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    val port: Int

    init {
        val registrar =
            LoggedToolRegistrar(sdk, RecordingServerLogRepository(), inFlightToolCalls = InFlightToolCalls())
        registrar.addTool("ping", "ping", "pong", ToolSchema()) {
            CallToolResult(content = listOf(TextContent(text = "pong")))
        }
        registrar.addTool("fail", "fail", "tool error", ToolSchema()) {
            CallToolResult(content = listOf(TextContent(text = "boom")), isError = true)
        }
        server =
            embeddedServer(
                Netty,
                configure = {
                    if (keyStore == null) {
                        connector {
                            host = "127.0.0.1"
                            this.port = requestedPort
                        }
                    } else {
                        sslConnector(
                            keyStore = keyStore,
                            keyAlias = CertificateManager.KEY_ALIAS,
                            keyStorePassword = { keyStorePassword },
                            privateKeyPassword = { keyStorePassword },
                        ) {
                            host = "127.0.0.1"
                            this.port = requestedPort
                        }
                    }
                },
            ) {
                installMcpBasePlugins { expectedToken = token }
                installMcpStatelessTransport(inFlightToolCalls = InFlightToolCalls()) { sdk }
            }.start(wait = false)
        port =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
    }

    override fun close() {
        server.stop(GRACE_MS, TIMEOUT_MS)
    }

    companion object {
        const val TOKEN = "assistant-test-token"
        private const val GRACE_MS = 100L
        private const val TIMEOUT_MS = 1_000L
    }
}
