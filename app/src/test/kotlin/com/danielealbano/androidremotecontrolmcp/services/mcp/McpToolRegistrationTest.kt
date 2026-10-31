package com.danielealbano.androidremotecontrolmcp.services.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ToolPermissionsConfig
import com.danielealbano.androidremotecontrolmcp.integration.McpIntegrationTestHelper
import com.danielealbano.androidremotecontrolmcp.mcp.tools.InFlightToolCallSignal
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.services.accessibility.CompactTreeFormatter
import com.danielealbano.androidremotecontrolmcp.services.accessibility.WebViewNodeMerger
import com.danielealbano.androidremotecontrolmcp.services.screencapture.ScreenshotRedactor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.server.ClientConnection
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("McpToolRegistration")
class McpToolRegistrationTest {
    private val deps = McpIntegrationTestHelper.createMockDependencies()
    private val signal = InFlightToolCallSignal()
    private val registration =
        McpToolRegistration(
            deps.serverLog,
            deps.inFlightToolCalls,
            AccessibilityToolDeps(
                deps.actionExecutor,
                deps.accessibilityServiceProvider,
                deps.treeParser,
                deps.elementFinder,
                deps.nodeCache,
                deps.typeInputController,
                signal,
            ),
            ScreenToolDeps(
                deps.screenCaptureProvider,
                CompactTreeFormatter(),
                deps.screenshotAnnotator,
                deps.screenshotEncoder,
                deps.screenStateSnapshotCache,
                WebViewNodeMerger(),
            ),
            PrivacyToolDeps(deps.privacyToolGate, deps.placeholderSubstitutor, ScreenshotRedactor()),
            DeviceToolDeps(
                deps.storageLocationProvider,
                deps.fileOperationProvider,
                deps.appManager,
                deps.cameraProvider,
                deps.intentDispatcher,
                deps.locationProvider,
            ),
            InboxToolDeps(deps.notificationProvider, deps.sharedContentInbox, deps.ephemeralFileLinkService),
        )

    private fun registerAll(
        deviceSlug: String = "",
        perms: ToolPermissionsConfig = ToolPermissionsConfig(),
        toolCallIndicatorEnabled: Boolean = false,
    ): Server {
        val server =
            Server(
                serverInfo = Implementation(name = "test", version = "test"),
                options =
                    ServerOptions(
                        capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)),
                    ),
            )
        registration.registerAll(
            server,
            McpToolUtils.buildToolNamePrefix(deviceSlug),
            perms,
            ServerConfig.DEFAULT_FILE_SIZE_LIMIT_MB,
            toolCallIndicatorEnabled = toolCallIndicatorEnabled,
        ) { McpIntegrationTestHelper.TEST_BASE_URL }
        return server
    }

    /** The tool names [McpToolRegistration.registerAll] puts on a fresh SDK server. */
    private fun registered(
        deviceSlug: String = "",
        perms: ToolPermissionsConfig = ToolPermissionsConfig(),
    ): Set<String> = registerAll(deviceSlug, perms).tools.keys.toSet()

    /** Calls the registered `press_back` tool; returns whether the signal was active while it ran. */
    private suspend fun callPressBackObservingSignal(
        indicatorEnabled: Boolean,
        outcome: () -> Result<Unit> = { Result.success(Unit) },
    ): Boolean {
        var observed = false
        every { deps.accessibilityServiceProvider.isReady() } returns true
        coEvery { deps.actionExecutor.pressBack() } coAnswers {
            observed = signal.active.value
            outcome()
        }
        val handler =
            registerAll(toolCallIndicatorEnabled = indicatorEnabled).tools.getValue("android_press_back").handler
        val connection = mockk<ClientConnection>(relaxed = true)

        handler(connection, mockk<CallToolRequest>(relaxed = true))

        return observed
    }

    @Test
    fun `tool call drives the in-flight signal`() =
        runTest {
            assertTrue(callPressBackObservingSignal(indicatorEnabled = true))
            assertFalse(signal.active.value)
        }

    @Test
    fun `signal runs when the visual indicator is disabled`() =
        runTest {
            assertTrue(callPressBackObservingSignal(indicatorEnabled = false))
            assertFalse(signal.active.value)
        }

    @Test
    fun `signal is balanced when the tool fails`() =
        runTest {
            callPressBackObservingSignal(indicatorEnabled = false) { Result.failure(IllegalStateException("boom")) }
            assertFalse(signal.active.value)
        }

    @Test
    fun `registers the same tools as the integration wiring`() {
        val expected = McpIntegrationTestHelper.createSdkServer(deps).tools.keys

        val actual = registered()

        assertTrue(expected.isNotEmpty())
        assertEquals(expected, actual)
    }

    @Test
    fun `every tool carries the device prefix`() {
        val prefix = McpToolUtils.buildToolNamePrefix(DEVICE_SLUG)

        val actual = registered(deviceSlug = DEVICE_SLUG)

        assertEquals(McpIntegrationTestHelper.createSdkServer(deps, DEVICE_SLUG).tools.keys, actual)
        assertTrue(actual.all { it.startsWith(prefix) }, "$actual")
    }

    @Test
    fun `a disabled tool is left out`() {
        val all = registered()
        val name = McpToolUtils.buildToolNamePrefix("") + DISABLED_TOOL

        val actual = registered(perms = ToolPermissionsConfig(disabledTools = setOf(DISABLED_TOOL)))

        assertTrue(name in all, "$all")
        assertFalse(name in actual, "$actual")
        assertEquals(all - name, actual)
    }

    private companion object {
        const val DEVICE_SLUG = "pixel"
        const val DISABLED_TOOL = "press_home"
    }
}
