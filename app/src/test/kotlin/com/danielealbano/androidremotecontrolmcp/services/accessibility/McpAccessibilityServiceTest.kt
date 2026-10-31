package com.danielealbano.androidremotecontrolmcp.services.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("McpAccessibilityService cache-invalidation wiring")
class McpAccessibilityServiceTest {
    @Nested
    @DisplayName("triggersCacheInvalidation")
    inner class TriggersCacheInvalidation {
        @Test
        @DisplayName("window state changed (rotation / activity transition) triggers invalidation")
        fun `window state changed triggers invalidation`() {
            assertTrue(triggersCacheInvalidation(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        }

        @Test
        @DisplayName("windows changed (keyboard show / hide) triggers invalidation")
        fun `windows changed triggers invalidation`() {
            assertTrue(triggersCacheInvalidation(AccessibilityEvent.TYPE_WINDOWS_CHANGED))
        }

        @Test
        @DisplayName("window content changed does NOT trigger invalidation (too frequent)")
        fun `window content changed does not trigger invalidation`() {
            assertFalse(triggersCacheInvalidation(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        }

        @ParameterizedTest
        @ValueSource(
            ints = [
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            ],
        )
        @DisplayName("unrelated event types do NOT trigger invalidation")
        fun `unrelated event types do not trigger invalidation`(eventType: Int) {
            assertFalse(triggersCacheInvalidation(eventType))
        }
    }

    @Nested
    @DisplayName("scheduleCacheInvalidationIfNeeded")
    inner class ScheduleCacheInvalidationIfNeeded {
        @Test
        @DisplayName("schedules the debouncer on a triggering event")
        fun `schedules on a triggering event`() {
            val debouncer = mockk<CacheInvalidationDebouncer>(relaxed = true)

            scheduleCacheInvalidationIfNeeded(AccessibilityEvent.TYPE_WINDOWS_CHANGED, debouncer)

            verify(exactly = 1) { debouncer.schedule() }
        }

        @Test
        @DisplayName("does NOT schedule on a non-triggering event")
        fun `does not schedule on a non-triggering event`() {
            val debouncer = mockk<CacheInvalidationDebouncer>(relaxed = true)

            scheduleCacheInvalidationIfNeeded(
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                debouncer,
            )

            verify(exactly = 0) { debouncer.schedule() }
        }

        @Test
        @DisplayName("is a safe no-op when the debouncer is null")
        fun `is a safe no-op when the debouncer is null`() {
            assertDoesNotThrow {
                scheduleCacheInvalidationIfNeeded(AccessibilityEvent.TYPE_WINDOWS_CHANGED, null)
            }
        }
    }

    @Nested
    @DisplayName("invalidateCache")
    inner class InvalidateCache {
        @Test
        @DisplayName("clears the provided cache")
        fun `clears the provided cache`() {
            val cache = mockk<AccessibilityNodeCache>(relaxed = true)

            invalidateCache(cache)

            verify(exactly = 1) { cache.clear() }
        }

        @Test
        @DisplayName("is a safe no-op when the cache is null")
        fun `is a safe no-op when the cache is null`() {
            assertDoesNotThrow { invalidateCache(null) }
        }
    }

    @Nested
    @DisplayName("own overlay hiding")
    inner class OwnOverlayHiding {
        private val own = "com.example.own"
        private val overlay = AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
        private val debouncer = mockk<CacheInvalidationDebouncer>(relaxed = true)
        private lateinit var service: McpAccessibilityService

        @BeforeEach
        fun setUp() {
            service = spyk(McpAccessibilityService())
            every { service.packageName } returns own
            setField("cacheInvalidationDebouncer", debouncer)
        }

        @AfterEach
        fun tearDown() {
            McpAccessibilityService.assistantOverlayHider = AssistantOverlayHider.NONE
        }

        private fun setField(
            name: String,
            value: Any?,
        ) {
            val field = McpAccessibilityService::class.java.getDeclaredField(name)
            field.isAccessible = true
            field.set(service, value)
        }

        private fun window(
            type: Int,
            title: String? = null,
            id: Int = 1,
        ): AccessibilityWindowInfo =
            mockk {
                every { this@mockk.type } returns type
                every { this@mockk.id } returns id
                every { this@mockk.title } returns title
                every { root } returns null
            }

        private fun stateEvent(
            pkg: String,
            windowId: Int,
        ): AccessibilityEvent =
            mockk {
                every { eventType } returns AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                every { packageName } returns pkg
                every { className } returns "$pkg.Activity"
                every { this@mockk.windowId } returns windowId
            }

        @Test
        fun `getAccessibilityWindows drops own overlay`() {
            val app = window(AccessibilityWindowInfo.TYPE_APPLICATION, id = 1)
            val mine = window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, id = 2)
            val other = window(overlay, "other", id = 3)
            every { service.windows } returns listOf(app, mine, other)

            assertEquals(listOf(app, other), service.getAccessibilityWindows())
        }

        @Test
        fun `getAccessibilityWindows returns empty when getWindows throws`() {
            every { service.windows } throws IllegalStateException("boom")

            assertEquals(emptyList<AccessibilityWindowInfo>(), service.getAccessibilityWindows())
        }

        @Test
        fun `getRootNode returns null for own overlay root`() {
            val root =
                mockk<AccessibilityNodeInfo> {
                    every { window } returns window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE)
                    every { packageName } returns own
                }
            every { service.rootInActiveWindow } returns root

            assertNull(service.getRootNode())
        }

        @Test
        fun `getRootNode returns the root of another app`() {
            val root =
                mockk<AccessibilityNodeInfo> {
                    every { window } returns window(AccessibilityWindowInfo.TYPE_APPLICATION)
                    every { packageName } returns "com.other"
                }
            every { service.rootInActiveWindow } returns root

            assertSame(root, service.getRootNode())
        }

        @Test
        fun `own overlay state event leaves current package unchanged`() {
            every { service.windows } returns listOf(window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, id = 5))
            service.onAccessibilityEvent(stateEvent("com.before", 9))

            service.onAccessibilityEvent(stateEvent(own, 5))

            assertEquals("com.before", service.getCurrentPackageName())
        }

        @Test
        fun `another app's state event updates current package`() {
            service.onAccessibilityEvent(stateEvent("com.other", 9))

            assertEquals("com.other", service.getCurrentPackageName())
        }

        @Test
        fun `own overlay events still schedule invalidation`() {
            every { service.windows } returns listOf(window(overlay, OWN_OVERLAY_ACCESSIBILITY_TITLE, id = 5))

            service.onAccessibilityEvent(stateEvent(own, 5))

            verify(exactly = 1) { debouncer.schedule() }
        }

        @Test
        fun `takeScreenshotBitmap runs inside the hider`() =
            runTest {
                val events = mutableListOf<String>()
                McpAccessibilityService.assistantOverlayHider =
                    object : AssistantOverlayHider {
                        override suspend fun <T> hiddenDuring(block: suspend () -> T): T {
                            events += "enter"
                            try {
                                return block()
                            } finally {
                                events += "exit"
                            }
                        }
                    }
                every { service.takeScreenshot(any(), any(), any()) } answers {
                    events += "capture"
                    thirdArg<AccessibilityService.TakeScreenshotCallback>().onFailure(1)
                }

                service.takeScreenshotBitmap()

                assertEquals(listOf("enter", "capture", "exit"), events)
            }
    }
}
