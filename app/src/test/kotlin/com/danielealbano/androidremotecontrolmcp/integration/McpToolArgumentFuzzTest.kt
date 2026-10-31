package com.danielealbano.androidremotecontrolmcp.integration

import android.os.SystemClock
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzCall
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzTool
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzToolCalls
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzToolMocks
import com.danielealbano.androidremotecontrolmcp.testutil.MockInvocationScanner
import com.danielealbano.androidremotecontrolmcp.testutil.Sentinel
import com.danielealbano.androidremotecontrolmcp.testutil.ToolClassification
import com.danielealbano.androidremotecontrolmcp.testutil.ToolResponse
import com.danielealbano.androidremotecontrolmcp.testutil.ToolResponseJudge
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Seeded property fuzz of every tool advertised by `tools/list` (enumerated at run time, so new tools are covered
 * automatically) through the real Streamable HTTP routing, bearer auth and SDK dispatch, with the Android services
 * mocked. Arguments are generated from each tool's input schema, conforming and mutated.
 *
 * Properties: a call answers a `CallToolResult` or a `-32602` JSON-RPC error and never a 5xx, a hang or a
 * transport failure; a value outside a declared or documented bound, an enum value or a wrongly typed value never
 * reaches a mocked service; text answers never echo a raw control character injected through an argument;
 * device-content tools put the untrusted-content warning first.
 */
@DisplayName("MCP tool argument fuzz")
class McpToolArgumentFuzzTest {
    @BeforeEach
    fun setUp() {
        McpIntegrationTestHelper.mockAndroidLog()
        // The stubbed android.jar returns 0 from SystemClock, which would make every polling tool loop forever.
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { System.nanoTime() / NANOS_PER_MILLI }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(SystemClock::class)
        McpIntegrationTestHelper.unmockAndroidLog()
    }

    @Test
    fun `every advertised tool is classified as device-content or action-only`() =
        runBlocking {
            McpIntegrationTestHelper.withRawTestApplication { _ ->
                val known = ToolClassification.DEVICE_CONTENT + ToolClassification.ACTION_ONLY
                val unclassified = listTools().map { it.name }.filter { it !in known }
                assertTrue(unclassified.isEmpty(), "unclassified tools: $unclassified")
            }
        }

    @Test
    fun `schema conforming arguments always get a tool result or a -32602 error`() =
        runFuzz(SEED_CONFORMING, CONFORMING_PER_TOOL) { calls, tool, i -> listOf(calls.conforming(tool, i)) }

    @Test
    fun `mutated arguments always get a tool result or a -32602 error and never break a bound`() =
        runFuzz(SEED_MUTATED, MUTATED_PER_TOOL) { calls, tool, i -> listOf(calls.mutated(tool, i)) }

    @Test
    fun `a single out-of-contract value never reaches a mocked service`() =
        runFuzz(SEED_BOUNDARY, 1) { calls, tool, _ -> calls.boundary(tool, BOUNDARY_PER_PARAM) }

    @Test
    fun `positive control - a sentinel value that reaches a mock is reported`() =
        runBlocking {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            coEvery { deps.actionExecutor.longPress(any(), any(), any()) } returns Result.success(Unit)
            val mocks = FuzzToolMocks.serviceMocks(deps)
            MockInvocationScanner.clear(mocks)
            deps.actionExecutor.longPress(1f, 1f, CONTROL_SENTINEL)
            val sentinel = Sentinel.Number("duration", CONTROL_SENTINEL.toDouble())
            val hits = MockInvocationScanner.hits(mocks, listOf(sentinel))
            assertEquals(1, hits.size, "the recording must see a value that reached a mocked service")
        }

    @Test
    fun `a number-typed parameter that accepts a whole value also accepts a fractional one`() =
        runBlocking {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            FuzzToolMocks.stub(deps)
            val rejected = mutableListOf<String>()
            McpIntegrationTestHelper.withRawTestApplication(deps) { _ ->
                val calls = FuzzToolCalls(Random(SEED_NUMBER))
                for (tool in listTools()) {
                    for ((param, schema) in tool.properties) {
                        if (schema["type"]?.jsonPrimitive?.contentOrNull != "number") continue
                        val base = calls.conformingArguments(tool).toMutableMap()
                        base[param] = JsonPrimitive(FRACTION_BASE)
                        val whole = isOk(callTool(tool.name, JsonObject(base), 1))
                        base[param] = JsonPrimitive(FRACTION_BASE + FRACTION)
                        val fractional = isOk(callTool(tool.name, JsonObject(base), 2))
                        if (whole && !fractional) rejected.add("${tool.name}.$param")
                    }
                }
            }
            assertTrue(rejected.isEmpty(), "schema says number but a fractional value is rejected: $rejected")
        }

    private fun isOk(response: ToolResponse): Boolean {
        val result = response.body?.get("result")?.jsonObject ?: return false
        return result["isError"]?.jsonPrimitive?.booleanOrNull != true
    }

    private fun runFuzz(
        seed: Int,
        perTool: Int,
        plan: (FuzzToolCalls, FuzzTool, Int) -> List<FuzzCall>,
    ) = runBlocking {
        val violations = linkedMapOf<String, String>()
        val outcomes = sortedMapOf<String, Int>()
        val deps = McpIntegrationTestHelper.createMockDependencies()
        FuzzToolMocks.stub(deps)
        val judge = ToolResponseJudge(violations)
        var toolCount = 0
        var total = 0
        var sentinelCalls = 0
        var reachedServices = 0
        McpIntegrationTestHelper.withRawTestApplication(deps) { _ ->
            val tools = listTools()
            toolCount = tools.size
            val calls = FuzzToolCalls(Random(seed))
            val mocks = FuzzToolMocks.serviceMocks(deps)
            for (tool in tools) {
                for (iteration in 0 until perTool) {
                    for (call in plan(calls, tool, iteration)) {
                        val where = "seed=$seed tool=${tool.name} iteration=$iteration ${call.description}"
                        val withSentinels = call.sentinels.isNotEmpty()
                        if (withSentinels) MockInvocationScanner.clear(mocks)
                        total++
                        val kind = judge.judge(call, callTool(tool.name, call.arguments, total), where)
                        outcomes.merge("${tool.name} $kind", 1, Int::plus)
                        if (withSentinels) {
                            sentinelCalls++
                            if (MockInvocationScanner.recordedCallCount(mocks) > 0) reachedServices++
                            MockInvocationScanner.hits(mocks, call.sentinels).forEach { (sentinel, detail) ->
                                violations.putIfAbsent(
                                    "${tool.name}|${sentinel.param}|${sentinel::class.simpleName}",
                                    "[out-of-contract] $detail :: $where",
                                )
                            }
                        }
                    }
                }
            }
        }
        println("McpToolArgumentFuzzTest seed=$seed tools=$toolCount perTool=$perTool calls=$total")
        println("McpToolArgumentFuzzTest sentinelCalls=$sentinelCalls reached=$reachedServices outcomes=$outcomes")
        assertTrue(violations.isEmpty(), "${violations.size} violation kinds:\n" + violations.values.joinToString("\n"))
    }

    private suspend fun ApplicationTestBuilder.callTool(
        name: String,
        arguments: JsonElement,
        id: Int,
    ): ToolResponse {
        val body =
            JsonObject(
                mapOf(
                    "jsonrpc" to JsonPrimitive("2.0"),
                    "id" to JsonPrimitive(id),
                    "method" to JsonPrimitive("tools/call"),
                    "params" to JsonObject(mapOf("name" to JsonPrimitive(name), "arguments" to arguments)),
                ),
            )
        return post(body.toString())
    }

    private suspend fun ApplicationTestBuilder.post(
        text: String,
        timeoutMs: Long = CALL_TIMEOUT_MS,
    ): ToolResponse {
        val outcome =
            runCatching {
                withTimeout(timeoutMs) {
                    val response =
                        client.post("/mcp") {
                            header("Authorization", "Bearer ${McpIntegrationTestHelper.TEST_BEARER_TOKEN}")
                            header("Accept", "application/json, text/event-stream")
                            header("Content-Type", "application/json")
                            setBody(text)
                        }
                    val raw = response.bodyAsText()
                    val parsed = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                    ToolResponse(response.status.value, parsed, raw, null)
                }
            }
        return outcome.getOrElse { e ->
            val problem =
                if (e is TimeoutCancellationException) {
                    "HANG after ${timeoutMs}ms: ${e.message}"
                } else {
                    "TRANSPORT/SERVER EXCEPTION ${e::class.qualifiedName}: ${e.message}"
                }
            ToolResponse(0, null, "", problem)
        }
    }

    private suspend fun ApplicationTestBuilder.listTools(): List<FuzzTool> {
        // The first request also boots the test application: that one-time setup runs on its own budget, so the
        // per-call hang budget measures only the fuzzed tool calls.
        val response = post("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""", SETUP_TIMEOUT_MS)
        val tools =
            response.body
                ?.get("result")
                ?.jsonObject
                ?.get("tools")
                ?.jsonArray
                ?: error("tools/list failed: ${response.problem ?: response.raw.take(PREVIEW)}")
        return tools.map { FuzzTool.parse(it.jsonObject) }
    }

    private companion object {
        const val SEED_CONFORMING = 20_260_511
        const val SEED_MUTATED = 20_260_512
        const val SEED_BOUNDARY = 20_260_513
        const val SEED_NUMBER = 20_260_514
        const val BOUNDARY_PER_PARAM = 6
        const val CONFORMING_PER_TOOL = 4
        const val MUTATED_PER_TOOL = 20
        const val CALL_TIMEOUT_MS = 5_000L
        const val SETUP_TIMEOUT_MS = 60_000L
        const val NANOS_PER_MILLI = 1_000_000L
        const val CONTROL_SENTINEL = 99_999L
        const val FRACTION_BASE = 7.0
        const val FRACTION = 0.5
        const val PREVIEW = 300
    }
}
