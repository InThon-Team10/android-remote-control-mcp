package com.danielealbano.androidremotecontrolmcp.integration

import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry.Type.TOOL_CALL
import com.danielealbano.androidremotecontrolmcp.mcp.installMcpBasePlugins
import com.danielealbano.androidremotecontrolmcp.mcp.installMcpStatelessTransport
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzHttpRequests
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzRequest
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Seeded property fuzz of the real `/mcp` routing + bearer-token plugin + SDK JSON-RPC transport, wired as in
 * `McpServer.configureApplication` (same exclusions) over the in-process Ktor test engine.
 *
 * Properties: no 5xx and no server-side exception for client-caused input, no request hangs, error bodies are
 * JSON-RPC errors or the plain auth/JSON responses, a request without a valid token never reaches a tool while a
 * token is configured, and `/health` stays unauthenticated.
 */
@DisplayName("MCP HTTP layer fuzz")
class McpHttpFuzzTest {
    @BeforeEach
    fun setUp() {
        McpIntegrationTestHelper.mockAndroidLog()
    }

    @AfterEach
    fun tearDown() {
        McpIntegrationTestHelper.unmockAndroidLog()
    }

    @Test
    fun `token on - random requests never 5xx, never hang and never reach a tool without a valid token`() =
        runFuzz(tokenOn = true, seed = SEED_TOKEN_ON, iterations = ITERATIONS)

    @Test
    fun `token off - random requests never 5xx, never hang and return well formed bodies`() =
        runFuzz(tokenOn = false, seed = SEED_TOKEN_OFF, iterations = ITERATIONS)

    @Test
    fun `health endpoint stays unauthenticated and answers JSON whatever the headers`() =
        runBlocking {
            val rnd = Random(SEED_HEALTH)
            withFuzzApp(tokenOn = true) { _ ->
                repeat(HEALTH_ITERATIONS) { iteration ->
                    val headers = FuzzHttpRequests(rnd, TOKEN, listOf("x")).next().headers
                    val request =
                        FuzzRequest(
                            method = "GET",
                            path = "/health",
                            headers = headers.filter { it.first != "Authorization" },
                            body = ByteArray(0),
                            validAuth = false,
                            label = "health",
                        )
                    val outcome = send(request)
                    val where = "seed=$SEED_HEALTH iteration=$iteration request=$request"
                    if (outcome is Outcome.Delivered) {
                        assertTrue(
                            outcome.status == HTTP_OK || outcome.status == HTTP_UNAVAILABLE,
                            "health status ${outcome.status} $where",
                        )
                        val json = Json.parseToJsonElement(outcome.text).jsonObject
                        assertTrue(json.containsKey("status"), "health body without status: ${outcome.text} $where")
                    }
                }
            }
        }

    private fun runFuzz(
        tokenOn: Boolean,
        seed: Int,
        iterations: Int,
    ) = runBlocking {
        val rnd = Random(seed)
        val deps = McpIntegrationTestHelper.createMockDependencies()
        val toolNames = TOOL_NAMES
        val generator = FuzzHttpRequests(rnd, TOKEN, toolNames)
        var delivered = 0
        var rejectedByClient = 0
        withFuzzApp(tokenOn, deps) { _ ->
            repeat(iterations) { iteration ->
                val request = generator.next()
                val where = "seed=$seed iteration=$iteration tokenOn=$tokenOn request=$request"
                val toolCallsBefore = toolCallCount(deps)
                when (val outcome = send(request)) {
                    is Outcome.Rejected -> {
                        rejectedByClient++
                    }

                    is Outcome.Violation -> {
                        throw AssertionError("${outcome.reason} $where")
                    }

                    is Outcome.Delivered -> {
                        delivered++
                        assertWellFormed(outcome, where)
                        val toolCalls = toolCallCount(deps) - toolCallsBefore
                        if (tokenOn && !request.validAuth) {
                            assertEquals(0, toolCalls, "tool reached without a token (status ${outcome.status}) $where")
                        }
                    }
                }
            }
        }
        println("McpHttpFuzzTest tokenOn=$tokenOn seed=$seed n=$iterations ok=$delivered rejected=$rejectedByClient")
        assertTrue(delivered > iterations / 2, "too few delivered requests: $delivered of $iterations")
    }

    @Test
    fun `token on - bypass probes against odd paths never reach a tool`() =
        runBlocking {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            val generator = FuzzHttpRequests(Random(SEED_PROBE), TOKEN, listOf("android_press_home"))
            withFuzzApp(tokenOn = true, deps) { _ ->
                for (path in PROBE_PATHS) {
                    val request =
                        FuzzRequest(
                            method = "POST",
                            path = path,
                            headers = JSON_HEADERS,
                            body = generator.probeBody(),
                            validAuth = false,
                            label = "probe",
                        )
                    val outcome = send(request)
                    assertTrue(
                        deps.serverLog.entries.none { it.type == ServerLogEntry.Type.TOOL_CALL },
                        "tool reached without a token through path ${FuzzStrings.escape(path)} outcome=$outcome",
                    )
                }
            }
        }

    @Test
    fun `positive control - a valid token reaches a tool and an invalid one gets 401`() =
        runBlocking {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            val generator = FuzzHttpRequests(Random(SEED_PROBE), TOKEN, listOf("android_press_home"))
            val headers = JSON_HEADERS
            withFuzzApp(tokenOn = true, deps) { _ ->
                val denied = send(FuzzRequest("POST", "/mcp", headers, generator.probeBody(), false, "control"))
                assertEquals(401, (denied as Outcome.Delivered).status)
                assertTrue(deps.serverLog.entries.none { it.type == ServerLogEntry.Type.TOOL_CALL })
                val allowed = headers + ("Authorization" to "Bearer $TOKEN")
                val ok = send(FuzzRequest("POST", "/mcp", allowed, generator.probeBody(), true, "control"))
                assertEquals(200, (ok as Outcome.Delivered).status)
                assertTrue(deps.serverLog.entries.any { it.type == ServerLogEntry.Type.TOOL_CALL })
            }
        }

    private fun toolCallCount(deps: MockDependencies): Int = deps.serverLog.entries.count { it.type == TOOL_CALL }

    private fun assertWellFormed(
        outcome: Outcome.Delivered,
        where: String,
    ) {
        assertTrue(outcome.status < SERVER_ERROR, "server error $outcome $where")
        if (outcome.text.isBlank()) return
        val json =
            try {
                Json.parseToJsonElement(outcome.text)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw AssertionError("non-JSON body $outcome $where", e)
            }
        val objects = if (json is JsonArray) json.toList() else listOf(json)
        for (element in objects) {
            assertTrue(isKnownBody(element), "unrecognised body shape $outcome $where")
        }
    }

    private fun isKnownBody(element: JsonElement): Boolean {
        val obj = element as? JsonObject ?: return false
        val error = obj["error"]
        return when {
            obj["jsonrpc"] != null && error is JsonObject -> {
                (error["code"] as? JsonPrimitive)?.intOrNull != null && error["message"] is JsonPrimitive
            }

            obj["jsonrpc"] != null -> {
                obj.containsKey("result")
            }

            // The health response: {"status": "healthy", "version": "..."}.
            obj["status"] is JsonPrimitive -> {
                obj["version"] is JsonPrimitive
            }

            // The documented plain auth response: {"error": "...", "message": "..."}.
            else -> {
                error is JsonPrimitive && obj["message"] is JsonPrimitive
            }
        }
    }

    private sealed interface Outcome {
        class Delivered(
            val status: Int,
            val text: String,
        ) : Outcome {
            override fun toString(): String = "status=$status body=${text.take(PREVIEW)}"
        }

        class Rejected(
            val reason: String,
        ) : Outcome

        class Violation(
            val reason: String,
        ) : Outcome
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun ApplicationTestBuilder.send(fuzz: FuzzRequest): Outcome =
        try {
            val response: HttpResponse =
                withTimeout(REQUEST_TIMEOUT_MS) {
                    client.request("http://localhost${fuzz.path}") {
                        method = HttpMethod(fuzz.method)
                        fuzz.headers.forEach { (name, value) -> headers.append(name, value) }
                        if (fuzz.body.isNotEmpty()) setBody(fuzz.body)
                    }
                }
            val bytes = withTimeout(REQUEST_TIMEOUT_MS) { response.bodyAsBytes() }
            Outcome.Delivered(response.status.value, String(bytes, Charsets.UTF_8))
        } catch (e: TimeoutCancellationException) {
            Outcome.Violation("request hung for more than ${REQUEST_TIMEOUT_MS}ms: ${e.message}")
        } catch (e: Throwable) {
            if (isClientSide(e)) {
                Outcome.Rejected(e.toString())
            } else {
                Outcome.Violation(
                    "server-side exception ${e::class.qualifiedName}: ${e.message} " +
                        "at ${e.stackTrace.take(SERVER_TRACE).joinToString()}",
                )
            }
        }

    /**
     * A rejection raised while the client builds the request (illegal header, bad URL) never reached the server: the
     * first frame that is either this test or server code tells which side threw it.
     */
    private fun isClientSide(e: Throwable): Boolean {
        val prefixes = SERVER_FRAME_PREFIXES + TEST_FRAME_PREFIX
        val origin = e.stackTrace.firstOrNull { frame -> prefixes.any { frame.className.startsWith(it) } }
        return origin == null || origin.className.startsWith(TEST_FRAME_PREFIX)
    }

    private suspend fun withFuzzApp(
        tokenOn: Boolean,
        deps: MockDependencies = McpIntegrationTestHelper.createMockDependencies(),
        block: suspend ApplicationTestBuilder.(MockDependencies) -> Unit,
    ) {
        val sdkServer = McpIntegrationTestHelper.createSdkServer(deps)
        testApplication {
            application {
                installMcpBasePlugins {
                    bearerTokenEnabled = tokenOn
                    expectedToken = if (tokenOn) TOKEN else ""
                    excludedPaths = setOf("/health", "/register", "/token", "/authorize", "/authorize/status")
                    excludedPathPrefixes = setOf("/s/", "/.well-known/")
                }
                routing {
                    get("/health") {
                        call.respondText(
                            """{"status":"healthy","version":"test"}""",
                            io.ktor.http.ContentType.Application.Json,
                            HttpStatusCode.OK,
                        )
                    }
                    get("/s/{token}") { call.respondText("", status = HttpStatusCode.NotFound) }
                }
                installMcpStatelessTransport(inFlightToolCalls = deps.inFlightToolCalls) { sdkServer }
            }
            block(deps)
        }
    }

    private companion object {
        const val TOKEN = McpIntegrationTestHelper.TEST_BEARER_TOKEN
        const val SEED_TOKEN_ON = 20_260_501
        const val SEED_TOKEN_OFF = 20_260_502
        const val SEED_HEALTH = 20_260_503
        const val SEED_PROBE = 20_260_504
        const val ITERATIONS = 1_200
        const val HEALTH_ITERATIONS = 200
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val SERVER_ERROR = 500
        const val SERVER_TRACE = 6
        val SERVER_FRAME_PREFIXES =
            listOf("io.ktor.server.", "io.modelcontextprotocol.", "com.danielealbano.androidremotecontrolmcp.mcp.")
        val JSON_HEADERS =
            listOf("Content-Type" to "application/json", "Accept" to "application/json, text/event-stream")
        val TOOL_NAMES =
            listOf("android_press_home", "android_tap", "android_scroll", "android_read_file", "android_send_intent")
        const val TEST_FRAME_PREFIX = "com.danielealbano.androidremotecontrolmcp.integration."
        const val HTTP_OK = 200
        const val HTTP_UNAVAILABLE = 503
        const val PREVIEW = 300
        val PROBE_PATHS =
            listOf(
                "/mcp",
                "//mcp",
                "/mcp/",
                "/MCP",
                "/./mcp",
                "/.well-known/../mcp",
                "/.well-known/%2e%2e/mcp",
                "/.well-known/..%2fmcp",
                "/.well-known/%2e%2e%2fmcp",
                "/s/../mcp",
                "/s/%2e%2e/mcp",
                "/s/..%2fmcp",
                "/health/../mcp",
                "/health%2f..%2fmcp",
                "/%6dcp",
                "/mcp%2f",
                "/mcp;x=y",
                "/a/../mcp",
                "/.well-known/./../mcp",
                "/s/x/../../mcp",
            )
    }
}
