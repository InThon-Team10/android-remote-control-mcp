package com.danielealbano.androidremotecontrolmcp.integration

import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityCapabilities
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceStatus
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessibilityStatusIntegrationTest {
    @BeforeEach
    fun setUp() = McpIntegrationTestHelper.mockAndroidLog()

    @AfterEach
    fun tearDown() = McpIntegrationTestHelper.unmockAndroidLog()

    @Test
    fun `status tool is listed with an empty input schema`() =
        runTest {
            McpIntegrationTestHelper.withTestApplication { client, _ ->
                val tool = client.listTools().tools.single { it.name == TOOL_NAME }

                assertTrue(tool.inputSchema.properties.isNullOrEmpty())
                assertTrue(tool.inputSchema.required.isNullOrEmpty())
            }
        }

    @Test
    fun `disabled accessibility returns a successful diagnostic response`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            every { deps.accessibilityServiceProvider.isReady() } returns false
            coEvery { deps.accessibilityServiceProvider.getStatus() } returns AccessibilityServiceStatus()

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result = client.callTool(name = TOOL_NAME, arguments = emptyMap())
                val json = Json.parseToJsonElement((result.content.single() as TextContent).text).jsonObject

                assertNotEquals(true, result.isError)
                assertFalse(json.getValue("enabled").jsonPrimitive.boolean)
                assertFalse(json.getValue("connected").jsonPrimitive.boolean)
                assertFalse(json.getValue("active_window_available").jsonPrimitive.boolean)
                assertEquals(0, json.getValue("interactive_window_count").jsonPrimitive.int)
                coVerify(exactly = 1) { deps.accessibilityServiceProvider.getStatus() }
                verify(exactly = 0) { deps.accessibilityServiceProvider.isReady() }
                verify { deps.actionExecutor wasNot Called }
            }
        }

    @Test
    fun `connected accessibility exposes actual capability and window diagnostics`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            coEvery { deps.accessibilityServiceProvider.getStatus() } returns
                AccessibilityServiceStatus(
                    enabled = true,
                    connected = true,
                    capabilities = AccessibilityCapabilities(true, true, true, true, true),
                    activeWindowAvailable = true,
                    interactiveWindowCount = 2,
                )

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result = client.callTool(name = TOOL_NAME, arguments = emptyMap())
                val json = Json.parseToJsonElement((result.content.single() as TextContent).text).jsonObject

                assertNotEquals(true, result.isError)
                assertTrue(json.getValue("enabled").jsonPrimitive.boolean)
                assertTrue(json.getValue("connected").jsonPrimitive.boolean)
                assertTrue(json.getValue("active_window_available").jsonPrimitive.boolean)
                assertEquals(2, json.getValue("interactive_window_count").jsonPrimitive.int)
                val capabilities = json.getValue("capabilities").jsonObject
                assertEquals(EXPECTED_CAPABILITIES, capabilities.keys)
                assertTrue(capabilities.values.all { it.jsonPrimitive.boolean })
                assertEquals(EXPECTED_FIELDS, json.keys)
            }
        }

    @Test
    fun `enabled but disconnected service remains distinguishable`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            coEvery { deps.accessibilityServiceProvider.getStatus() } returns AccessibilityServiceStatus(enabled = true)

            McpIntegrationTestHelper.withTestApplication(deps) { client, _ ->
                val result = client.callTool(name = TOOL_NAME, arguments = emptyMap())
                val json = Json.parseToJsonElement((result.content.single() as TextContent).text).jsonObject

                assertTrue(json.getValue("enabled").jsonPrimitive.boolean)
                assertFalse(json.getValue("connected").jsonPrimitive.boolean)
            }
        }

    @Test
    fun `device slug applies to status registration and calls`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            coEvery { deps.accessibilityServiceProvider.getStatus() } returns AccessibilityServiceStatus()

            McpIntegrationTestHelper.withTestApplication(deps, deviceSlug = "test_phone") { client, _ ->
                val name = "android_test_phone_get_accessibility_status"
                assertTrue(client.listTools().tools.any { it.name == name })
                assertNotEquals(true, client.callTool(name = name, arguments = emptyMap()).isError)
            }
        }

    @Test
    fun `disabled status tool is absent and cannot be called`() =
        runTest {
            val deps = McpIntegrationTestHelper.createMockDependencies()
            val perms = ToolPermissionsConfig(disabledTools = setOf("get_accessibility_status"))

            McpIntegrationTestHelper.withTestApplication(deps, perms = perms) { client, _ ->
                assertFalse(client.listTools().tools.any { it.name == TOOL_NAME })
                assertTrue(client.callTool(name = TOOL_NAME, arguments = emptyMap()).isError == true)
                coVerify(exactly = 0) { deps.accessibilityServiceProvider.getStatus() }
            }
        }

    private companion object {
        const val TOOL_NAME = "android_get_accessibility_status"
        val EXPECTED_FIELDS =
            setOf("enabled", "connected", "capabilities", "active_window_available", "interactive_window_count")
        val EXPECTED_CAPABILITIES =
            setOf(
                "can_retrieve_window_content",
                "can_perform_gestures",
                "can_take_screenshot",
                "can_retrieve_interactive_windows",
                "can_report_view_ids",
            )
    }
}
