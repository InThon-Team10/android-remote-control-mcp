package com.danielealbano.androidremotecontrolmcp.integration

import com.danielealbano.androidremotecontrolmcp.mcp.InFlightToolCalls
import com.danielealbano.androidremotecontrolmcp.mcp.installMcpBasePlugins
import com.danielealbano.androidremotecontrolmcp.mcp.installMcpStatelessTransport
import com.danielealbano.androidremotecontrolmcp.mcp.tools.LoggedToolRegistrar
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Every POST to `/mcp` that passes authentication is served by a session of its own, closed once the request ends
 * whatever the transport answered: the requests the transport refuses leave nothing behind, so no number of them can
 * lock later clients out. Runs on the real Netty engine with bearer authentication on, as in production.
 */
@DisplayName("Stateless transport session cleanup")
class StatelessSessionCleanupIntegrationTest {
    private val inFlightToolCalls = InFlightToolCalls()
    private val sdk =
        Server(
            Implementation(name = "test", version = "1"),
            ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        )
    private val sessionsOpened = AtomicInteger()
    private val gateArrivals = AtomicInteger()
    private val allAtGate = CompletableDeferred<Unit>()
    private val gateOpen = CompletableDeferred<Unit>()
    private val client = OkHttpClient()
    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private var serverPort = 0

    /** A POST the transport refuses before serving it, and the status it answers with. */
    enum class Refused(
        val status: HttpStatusCode,
        val body: String,
        val accept: String = ACCEPT_JSON_AND_EVENT_STREAM,
        val contentType: String = JSON,
        val protocolVersion: String? = null,
    ) {
        ACCEPT_WITHOUT_EVENT_STREAM(HttpStatusCode.NotAcceptable, TOOL_CALL, accept = JSON),
        CONTENT_TYPE_NOT_JSON(HttpStatusCode.UnsupportedMediaType, TOOL_CALL, contentType = "text/plain"),
        MALFORMED_JSON(HttpStatusCode.BadRequest, "{not json"),
        JSON_NOT_A_MESSAGE(HttpStatusCode.BadRequest, "42"),
        BATCHED_INITIALIZE(HttpStatusCode.BadRequest, "[$INITIALIZE,$INITIALIZED]"),
        UNSUPPORTED_PROTOCOL_VERSION(HttpStatusCode.BadRequest, TOOL_CALL, protocolVersion = "1999-01-01"),
        BODY_TOO_LARGE(HttpStatusCode.PayloadTooLarge, OVERSIZED_TOOL_CALL),
    }

    private data class Answer(
        val status: Int,
        val body: String,
        val sessionId: String?,
    )

    @BeforeEach
    fun setUp() {
        sdk.onConnect { sessionsOpened.incrementAndGet() }
        val registrar = LoggedToolRegistrar(sdk, RecordingServerLogRepository(), inFlightToolCalls = inFlightToolCalls)
        registrar.addTool("fast", "fast", "answers at once", ToolSchema()) {
            CallToolResult(content = listOf(TextContent(text = "done")))
        }
        registrar.addTool("gate", "gate", "answers once the test opens the gate", ToolSchema()) {
            if (gateArrivals.incrementAndGet() == CONCURRENT_CALLS) allAtGate.complete(Unit)
            withTimeout(WAIT_MS) { gateOpen.await() }
            CallToolResult(content = listOf(TextContent(text = "done")))
        }
        server =
            embeddedServer(
                Netty,
                configure = {
                    connector {
                        host = LOOPBACK
                        port = 0
                    }
                },
            ) {
                installMcpBasePlugins { expectedToken = TOKEN }
                installMcpStatelessTransport(inFlightToolCalls = inFlightToolCalls) { sdk }
            }.start(wait = false)
        serverPort =
            runBlocking {
                server.engine
                    .resolvedConnectors()
                    .first()
                    .port
            }
    }

    @AfterEach
    fun tearDown() {
        server.stop(STOP_GRACE_MS, STOP_TIMEOUT_MS)
    }

    private fun mcpRequest(token: String? = TOKEN): Request.Builder =
        Request.Builder().url("http://$LOOPBACK:$serverPort/mcp").also { builder ->
            token?.let { builder.header("Authorization", "Bearer $it") }
        }

    private fun post(
        body: String,
        accept: String = ACCEPT_JSON_AND_EVENT_STREAM,
        contentType: String = JSON,
        protocolVersion: String? = null,
        token: String? = TOKEN,
    ): Request =
        mcpRequest(token)
            .header("Accept", accept)
            .also { builder -> protocolVersion?.let { builder.header("MCP-Protocol-Version", it) } }
            .post(body.toRequestBody(contentType.toMediaType()))
            .build()

    private fun post(refused: Refused): Request = with(refused) { post(body, accept, contentType, protocolVersion) }

    private fun send(request: Request): Answer =
        client.newCall(request).execute().use { response ->
            Answer(response.code, response.body.string(), response.header(MCP_SESSION_ID))
        }

    /**
     * The SDK server opened [opened] sessions, one for each POST that reached the transport, and holds none of them
     * anymore: every request's session was closed once the request ended.
     */
    private fun assertOpenedAndClosed(opened: Int) {
        assertEquals(opened, sessionsOpened.get())
        val deadline = System.currentTimeMillis() + WAIT_MS
        while (sdk.sessions.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        assertEquals(emptyMap<String, Any>(), sdk.sessions)
    }

    private fun assertToolCallAnswered(answer: Answer) {
        assertEquals(HttpStatusCode.OK.value, answer.status)
        assertTrue(answer.body.contains("done"), answer.body)
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Refused::class)
    fun `a POST the transport refuses is answered with its error and leaves no session behind`(refused: Refused) {
        val answer = send(post(refused))

        assertEquals(refused.status.value, answer.status)
        assertOpenedAndClosed(1)
    }

    @Test
    fun `a tool call with neither an initialize nor a session id is answered and leaves no session behind`() {
        val answer = send(post(TOOL_CALL))

        assertToolCallAnswered(answer)
        assertNull(answer.sessionId)
        assertOpenedAndClosed(1)
    }

    @Test
    fun `an initialize is answered without a session id and leaves no session behind`() {
        val answer = send(post(INITIALIZE))

        assertEquals(HttpStatusCode.OK.value, answer.status)
        assertTrue(answer.body.contains("protocolVersion"), answer.body)
        assertNull(answer.sessionId)
        assertOpenedAndClosed(1)
    }

    @Test
    fun `a POST of notifications only is accepted and leaves no session behind`() {
        val answer = send(post(INITIALIZED))

        assertEquals(HttpStatusCode.Accepted.value, answer.status)
        assertOpenedAndClosed(1)
    }

    @Test
    fun `refused POSTs, however many, do not stop later tool calls`() {
        // The oversized POST has its own case above: 4 MiB a time would only slow the burst down
        val refusals = Refused.entries - Refused.BODY_TOO_LARGE
        repeat(REFUSED_BURST) { i ->
            val refused = refusals[i % refusals.size]
            assertEquals(refused.status.value, send(post(refused)).status, refused.name)
        }

        assertToolCallAnswered(send(post(TOOL_CALL)))
        assertOpenedAndClosed(REFUSED_BURST + 1)
    }

    @Test
    fun `concurrent tool calls are all answered and leave no session behind`() {
        val answers = ConcurrentLinkedQueue<Answer>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val gateCall = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"gate","arguments":{}}}"""

        val callers =
            List(CONCURRENT_CALLS) {
                thread { runCatching { send(post(gateCall)) }.onSuccess(answers::add).onFailure(failures::add) }
            }
        runBlocking { withTimeout(WAIT_MS) { allAtGate.await() } }
        // Every call is running at once, each in a session of its own
        assertEquals(CONCURRENT_CALLS, sdk.sessions.size)
        gateOpen.complete(Unit)
        callers.forEach { it.join() }

        assertEquals(emptyList<Throwable>(), failures.toList())
        assertEquals(CONCURRENT_CALLS, answers.size)
        answers.forEach { assertToolCallAnswered(it) }
        assertOpenedAndClosed(CONCURRENT_CALLS)
    }

    @Test
    fun `requests refused before the transport never open a session`() {
        assertEquals(HttpStatusCode.Unauthorized.value, send(post(TOOL_CALL, token = null)).status)
        assertEquals(HttpStatusCode.Unauthorized.value, send(post(TOOL_CALL, token = "wrong")).status)
        assertEquals(HttpStatusCode.MethodNotAllowed.value, send(mcpRequest().get().build()).status)
        assertEquals(HttpStatusCode.MethodNotAllowed.value, send(mcpRequest().delete().build()).status)

        assertEquals(0, sessionsOpened.get())
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val TOKEN = "session-cleanup-test-token"
        const val MCP_SESSION_ID = "mcp-session-id"
        const val JSON = "application/json"
        const val ACCEPT_JSON_AND_EVENT_STREAM = "application/json, text/event-stream"
        const val TOOL_CALL =
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"fast","arguments":{}}}"""
        const val INITIALIZE =
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",""" +
                """"capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""
        const val INITIALIZED = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""

        /** The SDK transport's default request body limit (4 MiB). */
        const val BODY_LIMIT_BYTES = 4 * 1024 * 1024
        val OVERSIZED_TOOL_CALL =
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"fast","arguments":""" +
                """{"pad":"${"x".repeat(BODY_LIMIT_BYTES)}"}}}"""

        /** Above the 100-session cap of the former session-based endpoint, which refused POSTs used to fill up. */
        const val REFUSED_BURST = 150
        const val CONCURRENT_CALLS = 32
        const val WAIT_MS = 5_000L
        const val POLL_MS = 50L
        const val STOP_GRACE_MS = 100L
        const val STOP_TIMEOUT_MS = 1_000L
    }
}
