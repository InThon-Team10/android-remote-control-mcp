package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.accessibility.AccessibilityEvent
import com.danielealbano.androidremotecontrolmcp.services.guidance.UserActivity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("guidanceActivityFor")
class McpAccessibilityServiceGuidanceTest {
    private fun activity(
        eventType: Int,
        eventPackage: String? = OTHER,
        className: String? = null,
    ) = guidanceActivityFor(eventType, eventPackage, OWN, className)

    @Test
    fun `maps the user event types of other apps`() {
        assertEquals(UserActivity.CLICKED, activity(AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertEquals(UserActivity.LONG_CLICKED, activity(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED))
        assertEquals(UserActivity.TEXT_CHANGED, activity(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
        assertEquals(UserActivity.SCROLLED, activity(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertEquals(UserActivity.WINDOW_CHANGED, activity(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
    }

    @Test
    fun `ignores events of this app and its overlays`() {
        listOf(
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        ).forEach { assertNull(activity(it, eventPackage = OWN), "$it") }
    }

    @Test
    fun `ignores event types that are not user activity`() {
        assertNull(activity(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        assertNull(activity(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
        assertNull(activity(AccessibilityEvent.TYPE_VIEW_FOCUSED))
    }

    @Test
    fun `ignores the keyboard window opening`() {
        val keyboard = "android.inputmethodservice.SoftInputWindow"

        assertNull(activity(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, className = keyboard))
    }

    private companion object {
        const val OWN = "com.example.own"
        const val OTHER = "com.example.other"
    }
}
