package com.danielealbano.androidremotecontrolmcp.services.guidance

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.concurrent.TimeUnit

class DeepSeekGuidancePlannerTest {
    private fun envelope(content: String) =
        buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(buildJsonObject { putJsonObject("message") { put("content", content) } })
                },
            )
        }.toString()

    @Test
    fun `uses requested model endpoint and bearer key and returns guidance without action tools`() =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .body(
                            envelope(
                                """{"status":"guide","instruction":"카카오톡을 누르세요","node_id":"node123"}""",
                            ),
                        ).build(),
                )
                val planner = DeepSeekGuidancePlanner(OkHttpClient(), server.url("/v1/chat/completions").toString())
                val decision = planner.plan("test-only-key", GuidanceState(goal = "카카오톡 켜는법"), "screen data")
                assertEquals("node123", decision.nodeId)
                val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("/v1/chat/completions", request.url.encodedPath)
                assertEquals("Bearer test-only-key", request.headers["Authorization"])
                val payload = Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject
                assertEquals("DeepSeek-V4.1-Flash-B300", payload.getValue("model").jsonPrimitive.content)
                assertFalse(payload.containsKey("tools"))
            }
        }

    @Test
    fun `provider errors expose status without echoing error body`() =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(401)
                        .body("private-account-detail")
                        .build(),
                )
                val planner = DeepSeekGuidancePlanner(OkHttpClient(), server.url("/").toString())
                val error = assertThrows<IOException> { planner.plan("fake-key", GuidanceState(), "screen") }
                assertTrue(error.message.orEmpty().contains("401"))
                assertFalse(error.message.orEmpty().contains("private-account-detail"))
            }
        }

    @Test
    fun `invalid or executable model outputs are rejected`() {
        val planner = DeepSeekGuidancePlanner()
        listOf(
            """{"status":"tap","node_id":"x"}""",
            """{"status":"guide","instruction":"tap"}""",
            """{"status":"guide","instruction":"","node_id":"x"}""",
            """{"status":"guide","instruction":"tap","node_id":"x","bounds":{"left":1,"top":2,"right":3,"bottom":4}}""",
        ).forEach { content ->
            assertThrows<IllegalArgumentException> { planner.decodeDecision(envelope(content)) }
        }
        assertEquals("complete", planner.decodeDecision(envelope("""{"status":"complete"}""")).status)
    }
}
