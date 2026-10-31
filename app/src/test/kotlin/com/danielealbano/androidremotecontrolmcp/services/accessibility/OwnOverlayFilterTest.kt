package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OwnOverlayFilter")
class OwnOverlayFilterTest {
    private val own = "com.example.own"
    private val overlay = AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
    private val stateChanged = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

    private fun window(
        type: Int,
        title: String? = null,
        rootPackage: String? = null,
        id: Int = 1,
    ): AccessibilityWindowInfo {
        val root =
            rootPackage?.let { pkg ->
                mockk<AccessibilityNodeInfo> { every { packageName } returns pkg }
            }
        return mockk {
            every { this@mockk.type } returns type
            every { this@mockk.id } returns id
            every { this@mockk.title } returns title
            every { this@mockk.root } returns root
        }
    }

    private fun root(
        window: AccessibilityWindowInfo?,
        pkg: String,
    ): AccessibilityNodeInfo =
        mockk {
            every { this@mockk.window } returns window
            every { packageName } returns pkg
        }

    @Test
    fun `isOwnOverlayWindow true for overlay with own title`() {
        assertTrue(isOwnOverlayWindow(window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, "com.other"), own))
        assertTrue(isOwnOverlayWindow(window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE), own))
    }

    @Test
    fun `isOwnOverlayWindow true for overlay with own root package`() {
        assertTrue(isOwnOverlayWindow(window(overlay, rootPackage = own), own))
    }

    @Test
    fun `isOwnOverlayWindow false for rootless overlay without own title`() {
        assertFalse(isOwnOverlayWindow(window(overlay), own))
    }

    @Test
    fun `isOwnOverlayWindow false for overlay of another package`() {
        assertFalse(isOwnOverlayWindow(window(overlay, "other", "com.other"), own))
    }

    @Test
    fun `isOwnOverlayWindow false for own application window`() {
        val w = window(AccessibilityWindowInfo.TYPE_APPLICATION, rootPackage = own)
        assertFalse(isOwnOverlayWindow(w, own))
        verify(exactly = 0) { w.root }
    }

    @Test
    fun `filterOwnOverlayWindows drops own overlays and keeps order`() {
        val app = window(AccessibilityWindowInfo.TYPE_APPLICATION, rootPackage = own, id = 1)
        val mine = window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, id = 2)
        val other = window(overlay, rootPackage = "com.other", id = 3)
        val rootless = window(overlay, id = 4)

        assertEquals(listOf(app, other, rootless), filterOwnOverlayWindows(listOf(app, mine, other, rootless), own))
    }

    @Test
    fun `filterOwnOverlayWindows keeps everything when none match and returns empty for empty`() {
        val other = window(overlay, rootPackage = "com.other")
        assertEquals(listOf(other), filterOwnOverlayWindows(listOf(other), own))
        assertTrue(filterOwnOverlayWindows(emptyList(), own).isEmpty())
    }

    @Test
    fun `isOwnOverlayRoot true for own overlay root`() {
        assertTrue(isOwnOverlayRoot(root(window(overlay), own), own))
    }

    @Test
    fun `isOwnOverlayRoot true for overlay root with own title`() {
        assertTrue(isOwnOverlayRoot(root(window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE), "com.other"), own))
    }

    @Test
    fun `isOwnOverlayRoot false for another package's overlay root`() {
        assertFalse(isOwnOverlayRoot(root(window(overlay, "x"), "com.other"), own))
    }

    @Test
    fun `isOwnOverlayRoot false for own application root`() {
        assertFalse(isOwnOverlayRoot(root(window(AccessibilityWindowInfo.TYPE_APPLICATION), own), own))
    }

    @Test
    fun `isOwnOverlayRoot false when window is null`() {
        assertFalse(isOwnOverlayRoot(root(null, own), own))
    }

    @Test
    fun `isOwnOverlayStateEvent true for own overlay state change`() {
        val w = window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, id = 7)
        assertTrue(isOwnOverlayStateEvent(stateChanged, own, 7, own) { listOf(w) })
    }

    @Test
    fun `isOwnOverlayStateEvent false for other event types`() {
        var fetched = false
        val markFetched = {
            fetched = true
            emptyList<AccessibilityWindowInfo>()
        }
        val types = listOf(AccessibilityEvent.TYPE_WINDOWS_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        types.forEach {
            assertFalse(isOwnOverlayStateEvent(it, own, 7, own, markFetched))
        }
        assertFalse(fetched)
    }

    @Test
    fun `isOwnOverlayStateEvent false for another package without fetching windows`() {
        var fetched = false
        val markFetched = {
            fetched = true
            emptyList<AccessibilityWindowInfo>()
        }
        assertFalse(isOwnOverlayStateEvent(stateChanged, "com.other", 7, own, markFetched))
        assertFalse(fetched)
    }

    @Test
    fun `isOwnOverlayStateEvent false for own app window`() {
        val w = window(AccessibilityWindowInfo.TYPE_APPLICATION, rootPackage = own, id = 7)
        assertFalse(isOwnOverlayStateEvent(stateChanged, own, 7, own) { listOf(w) })
    }

    @Test
    fun `isOwnOverlayStateEvent false when the window id is not listed`() {
        val w = window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, id = 8)
        assertFalse(isOwnOverlayStateEvent(stateChanged, own, 7, own) { listOf(w) })
    }
}
