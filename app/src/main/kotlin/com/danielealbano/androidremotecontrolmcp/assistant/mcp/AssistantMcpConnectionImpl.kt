package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.CertificateManager
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AssistantMcpConnection] over `StreamableHttpClientTransport`. The server is stateless per request, so a session
 * is cheap: any failed call drops it and the next call reconnects. The mutex guards only connect / drop / close,
 * never a running call, so [close] is never blocked by an in-flight tool call.
 */
@Singleton
class AssistantMcpConnectionImpl
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val certificateManager: CertificateManager,
    ) : AssistantMcpConnection {
        private class Session(
            val client: Client,
            val http: HttpClient,
        )

        private val mutex = Mutex()

        @Volatile
        private var session: Session? = null

        override suspend fun connect(): Result<Unit> {
            val connected = Result.success(Unit)
            return mutex.withLock { if (session != null) connected else open() }
        }

        override suspend fun listTools(): Result<List<Tool>> = withSession { it.listTools().tools }

        override suspend fun callTool(
            name: String,
            arguments: JsonObject,
        ): Result<CallToolResult> {
            val request = CallToolRequest(CallToolRequestParams(name = name, arguments = arguments))
            return withSession { it.callTool(request) }
        }

        override suspend fun close() {
            val closing = mutex.withLock { session.also { session = null } }
            closing?.let { dispose(it) }
        }

        private suspend fun open(): Result<Unit> {
            val config = settingsRepository.getServerConfig()
            return if (!config.bearerTokenEnabled || config.bearerToken.isBlank()) {
                Result.failure(AssistantException(AssistantError.BearerDisabled))
            } else {
                catching { establish(config) }.fold(
                    onSuccess = {
                        session = it
                        Logger.i(TAG, "Connected to the MCP server")
                        Result.success(Unit)
                    },
                    onFailure = {
                        Logger.w(TAG, "MCP connection failed: ${it.javaClass.simpleName}")
                        Result.failure(it.toAssistantException())
                    },
                )
            }
        }

        private suspend fun establish(config: ServerConfig): Session {
            val http = AssistantMcpEndpoint.httpClient(pinnedCertificate(config))
            val client = Client(clientInfo = Implementation(name = CLIENT_NAME, version = CLIENT_VERSION))
            val transport =
                StreamableHttpClientTransport(
                    client = http,
                    url = AssistantMcpEndpoint.url(config),
                    requestBuilder = { headers.append("Authorization", "Bearer ${config.bearerToken}") },
                )
            runCatching { client.connect(transport) }.onFailure { http.close() }.getOrThrow()
            return Session(client, http)
        }

        /** The server's own certificate (null for plain HTTP); loaded off the main thread (may generate it). */
        private suspend fun pinnedCertificate(config: ServerConfig): X509Certificate? =
            if (!config.httpsEnabled) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    certificateManager.getOrCreateKeyStore(config).getCertificate(CertificateManager.KEY_ALIAS)
                        as? X509Certificate
                        ?: throw IllegalStateException("Server certificate not found")
                }
            }

        private suspend fun <T> withSession(block: suspend (Client) -> T): Result<T> =
            connect().fold(
                onSuccess = { runOn(block) },
                onFailure = { Result.failure(it) },
            )

        private suspend fun <T> runOn(block: suspend (Client) -> T): Result<T> {
            val notConnected = AssistantException(AssistantError.McpUnavailable(NOT_CONNECTED))
            val current = session ?: return Result.failure(notConnected)
            return catching { block(current.client) }.fold(
                onSuccess = { Result.success(it) },
                onFailure = {
                    drop(current)
                    Result.failure(it.toAssistantException())
                },
            )
        }

        private suspend fun drop(failed: Session) {
            val dropped = mutex.withLock { if (session === failed) failed.also { session = null } else null }
            dropped?.let { dispose(it) }
        }

        private suspend fun dispose(closing: Session) {
            runCatching { closing.client.close() }
            closing.http.close()
        }

        private companion object {
            const val TAG = "MCP:AssistantConnection"
            const val CLIENT_NAME = "assistant"
            const val CLIENT_VERSION = "1.0.0"
            const val NOT_CONNECTED = "Not connected"
        }
    }

private suspend fun <T> catching(block: suspend () -> T): Result<T> =
    runCatching { block() }.onFailure { if (it is CancellationException) throw it }

private fun Throwable.toAssistantException(): AssistantException =
    this as? AssistantException
        ?: AssistantException(AssistantError.McpUnavailable(message ?: javaClass.simpleName))
