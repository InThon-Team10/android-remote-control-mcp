package com.danielealbano.androidremotecontrolmcp.integration

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** The millisecond parameters only accept whole numbers, so their advertised schema type must say integer. */
@DisplayName("Tool schema whole-number parameters")
class ToolSchemaWholeNumberIntegrationTest {
    @BeforeEach
    fun setUp() {
        McpIntegrationTestHelper.mockAndroidLog()
    }

    @AfterEach
    fun tearDown() {
        McpIntegrationTestHelper.unmockAndroidLog()
    }

    @Test
    fun `duration and time parameters are advertised as integers`() =
        runTest {
            McpIntegrationTestHelper.withTestApplication { client, _ ->
                val tools = client.listTools().tools.associateBy { it.name }
                for (tool in listOf("android_long_press", "android_swipe", "android_pinch")) {
                    val duration =
                        tools
                            .getValue(tool)
                            .inputSchema.properties!!
                            .getValue("duration")
                            .jsonObject
                    assertEquals("integer", duration.getValue("type").jsonPrimitive.content, "$tool.duration")
                }
                val point =
                    tools
                        .getValue("android_custom_gesture")
                        .inputSchema.properties!!
                        .getValue("paths")
                        .jsonObject
                        .getValue("items")
                        .jsonObject
                        .getValue("items")
                        .jsonObject
                        .getValue("properties")
                        .jsonObject
                assertEquals(
                    "integer",
                    point
                        .getValue("time")
                        .jsonObject
                        .getValue("type")
                        .jsonPrimitive.content,
                )
            }
        }

    @Test
    fun `a fractional duration is rejected as invalid params`() =
        runTest {
            McpIntegrationTestHelper.withTestApplication { client, _ ->
                val result =
                    client.callTool(
                        name = "android_long_press",
                        arguments = mapOf("x" to 10, "y" to 10, "duration" to 100.5),
                    )
                assertEquals(true, result.isError)
                assertTrue((result.content[0] as TextContent).text.contains("duration"))
            }
        }
}
