package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.accessibility.AccessibilityEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("McpAccessibilityService window state names")
class McpAccessibilityServiceWindowNamesTest {
    private val service = McpAccessibilityService()

    private fun windowStateChanged(
        packageName: CharSequence?,
        className: CharSequence?,
    ): AccessibilityEvent =
        mockk<AccessibilityEvent> {
            every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            every { this@mockk.packageName } returns packageName
            every { this@mockk.className } returns className
        }

    @Test
    fun `non-blank names update the tracked package and activity`() {
        service.onAccessibilityEvent(windowStateChanged("com.example.app", "com.example.app.Main"))

        assertEquals("com.example.app", service.getCurrentPackageName())
        assertEquals("com.example.app.Main", service.getCurrentActivityName())
    }

    @Test
    fun `blank names keep the previous package and activity`() {
        service.onAccessibilityEvent(windowStateChanged("com.example.app", "com.example.app.Main"))

        service.onAccessibilityEvent(windowStateChanged("", " "))

        assertEquals("com.example.app", service.getCurrentPackageName())
        assertEquals("com.example.app.Main", service.getCurrentActivityName())
    }

    @Test
    fun `null names keep the previous package and activity`() {
        service.onAccessibilityEvent(windowStateChanged("com.example.app", "com.example.app.Main"))

        service.onAccessibilityEvent(windowStateChanged(null, null))

        assertEquals("com.example.app", service.getCurrentPackageName())
        assertEquals("com.example.app.Main", service.getCurrentActivityName())
    }

    @Test
    fun `a blank package name does not block an activity update`() {
        service.onAccessibilityEvent(windowStateChanged("com.example.app", "com.example.app.Main"))

        service.onAccessibilityEvent(windowStateChanged("", "com.example.app.Settings"))

        assertEquals("com.example.app", service.getCurrentPackageName())
        assertEquals("com.example.app.Settings", service.getCurrentActivityName())
    }

    @Test
    fun `blank names on a fresh service leave both names null`() {
        service.onAccessibilityEvent(windowStateChanged("", ""))

        assertNull(service.getCurrentPackageName())
        assertNull(service.getCurrentActivityName())
    }
}
