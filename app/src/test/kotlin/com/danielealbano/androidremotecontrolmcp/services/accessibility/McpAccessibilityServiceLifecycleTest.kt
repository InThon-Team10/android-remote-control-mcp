package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import dagger.hilt.android.EntryPointAccessors
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("McpAccessibilityService lifecycle")
class McpAccessibilityServiceLifecycleTest {
    private val host = mockk<AccessibilityOverlayHost>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val entryPoint = mockk<McpAccessibilityService.NodeCacheEntryPoint>()
        every { entryPoint.nodeCache() } returns mockk<AccessibilityNodeCache>(relaxed = true)
        val guidanceEntryPoint = mockk<McpAccessibilityService.GuidanceEntryPoint>()
        every { guidanceEntryPoint.guidanceController() } returns mockk<GuidanceController>(relaxed = true)
        every { guidanceEntryPoint.overlayHosts() } returns setOf(host)
        mockkStatic(EntryPointAccessors::class)
        every {
            EntryPointAccessors.fromApplication(any(), McpAccessibilityService.NodeCacheEntryPoint::class.java)
        } returns entryPoint
        every {
            EntryPointAccessors.fromApplication(any(), McpAccessibilityService.GuidanceEntryPoint::class.java)
        } returns guidanceEntryPoint
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(EntryPointAccessors::class)
        Dispatchers.resetMain()
    }

    private fun newService(): McpAccessibilityService =
        spyk(McpAccessibilityService()).also {
            every { it.applicationContext } returns mockk<Context>()
        }

    private fun McpAccessibilityService.connect() {
        McpAccessibilityService::class.java
            .getDeclaredMethod("onServiceConnected")
            .apply { isAccessible = true }
            .invoke(this)
    }

    @Test
    @DisplayName("a stale instance's onDestroy does not clear a newer connected instance")
    fun `stale onDestroy keeps newer instance`() {
        val old = newService()
        val replacement = newService()
        old.connect()
        replacement.connect()

        old.onDestroy()

        assertSame(replacement, McpAccessibilityService.instance)
        replacement.onDestroy()
    }

    @Test
    @DisplayName("onServiceConnected twice cancels the previous service scope")
    fun `reconnect cancels previous scope`() {
        val service = newService()
        service.connect()
        val firstScope = service.getServiceScope()!!

        service.connect()

        assertFalse(firstScope.isActive)
        service.onDestroy()
    }

    @Test
    @DisplayName("overlay hosts are attached on connect and detached on destroy")
    fun `hosts are attached and detached`() {
        val service = newService()

        service.connect()
        verify(exactly = 1) { host.attach(service) }
        service.onDestroy()

        verify(exactly = 1) { host.detach() }
    }
}
