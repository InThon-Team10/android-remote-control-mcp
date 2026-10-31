package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccessibilityServiceProviderImplTest {
    private val context = mockk<Context>()
    private val service = mockk<McpAccessibilityService>()
    private val provider = AccessibilityServiceProviderImpl(context)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(McpAccessibilityService.Companion, PermissionUtils)
        every {
            PermissionUtils.isAccessibilityServiceEnabled(context, McpAccessibilityService::class.java)
        } returns true
        every { McpAccessibilityService.instance } returns service
        every { service.isReady() } returns true
        every { service.serviceInfo } returns null
        every { service.getRootNode() } returns null
        every { service.getAccessibilityWindows() } returns emptyList()
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(McpAccessibilityService.Companion, PermissionUtils)
        Dispatchers.resetMain()
    }

    @Test
    fun `disabled service returns diagnostics without querying windows`() =
        runTest {
            every { McpAccessibilityService.instance } returns null
            every {
                PermissionUtils.isAccessibilityServiceEnabled(context, McpAccessibilityService::class.java)
            } returns false

            assertEquals(AccessibilityServiceStatus(), provider.getStatus())
            verify(exactly = 0) { service.getRootNode() }
            verify(exactly = 0) { service.getAccessibilityWindows() }
        }

    @Test
    fun `enabled service waiting for binding is not connected`() =
        runTest {
            every { McpAccessibilityService.instance } returns null

            assertEquals(AccessibilityServiceStatus(enabled = true), provider.getStatus())
        }

    @Test
    fun `service that is not ready is not queried`() =
        runTest {
            every { service.isReady() } returns false

            assertEquals(AccessibilityServiceStatus(enabled = true), provider.getStatus())
            verify(exactly = 0) { service.serviceInfo }
            verify(exactly = 0) { service.getRootNode() }
        }

    @Test
    fun `connected service reports granted capabilities and available windows`() =
        runTest {
            val info = mockk<AccessibilityServiceInfo>()
            every { info.capabilities } returns (
                AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT or
                    AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES or
                    AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT
            )
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            every { service.serviceInfo } returns info
            every { service.getRootNode() } returns mockk()
            every { service.getAccessibilityWindows() } returns listOf(mockk(), mockk())

            val status = provider.getStatus()

            assertTrue(status.enabled)
            assertTrue(status.connected)
            assertEquals(AccessibilityCapabilities(true, true, true, true, true), status.capabilities)
            assertTrue(status.activeWindowAvailable)
            assertEquals(2, status.interactiveWindowCount)
        }

    @Test
    fun `connected service without active window remains connected`() =
        runTest {
            val status = provider.getStatus()

            assertTrue(status.connected)
            assertFalse(status.activeWindowAvailable)
            assertEquals(0, status.interactiveWindowCount)
            assertEquals(AccessibilityCapabilities(), status.capabilities)
        }

    @Test
    fun `background caller reads Android service on the main dispatcher`() =
        runTest {
            val mainThread = Thread.currentThread()
            every { service.getRootNode() } answers {
                assertEquals(mainThread, Thread.currentThread())
                null
            }

            val status = withContext(Dispatchers.Default) { provider.getStatus() }

            assertTrue(status.connected)
            verify(exactly = 1) { service.getRootNode() }
        }

    @Test
    fun `interactive window flag alone does not grant window content access`() =
        runTest {
            val info = mockk<AccessibilityServiceInfo>()
            every { info.capabilities } returns AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES
            info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            every { service.serviceInfo } returns info

            val capabilities = provider.getStatus().capabilities

            assertTrue(capabilities.canPerformGestures)
            assertFalse(capabilities.canRetrieveWindowContent)
            assertFalse(capabilities.canRetrieveInteractiveWindows)
            assertFalse(capabilities.canTakeScreenshot)
            assertFalse(capabilities.canReportViewIds)
        }

    @Test
    fun `window content capability alone does not enable interactive windows`() =
        runTest {
            val info = mockk<AccessibilityServiceInfo>()
            every { info.capabilities } returns AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT
            every { service.serviceInfo } returns info

            val capabilities = provider.getStatus().capabilities

            assertTrue(capabilities.canRetrieveWindowContent)
            assertFalse(capabilities.canRetrieveInteractiveWindows)
            assertFalse(capabilities.canPerformGestures)
        }
}
