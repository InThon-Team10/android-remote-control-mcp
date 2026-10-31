package com.danielealbano.androidremotecontrolmcp.services.guidance

import com.danielealbano.androidremotecontrolmcp.services.accessibility.McpAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Public integration point for a future ViewModel. All rendering and transitions run on Main. */
object GuidanceController {
    private val session = GuidanceSession()
    val state = session.state
    private var overlay: GuidanceOverlay? = null
    private var activeAnchor: GuidanceAnchor? = null

    suspend fun start(goal: String = GuidanceSession.TEST_GOAL): GuidanceState =
        withContext(Dispatchers.Main.immediate) {
            checkNotNull(McpAccessibilityService.instance) { "Enable the accessibility service first" }
            session.start(goal)
        }

    suspend fun show(
        revision: Long,
        instruction: String,
        bounds: HighlightBounds,
        anchor: GuidanceAnchor? = null,
    ): GuidanceState =
        withContext(Dispatchers.Main.immediate) {
            val service = checkNotNull(McpAccessibilityService.instance) { "Accessibility service disconnected" }
            val screen = service.getScreenInfo()
            require(bounds.right <= screen.width && bounds.bottom <= screen.height) {
                "Highlight is outside the screen"
            }
            val result = session.propose(revision, instruction, bounds)
            var rendered = false
            try {
                val renderer = overlay ?: GuidanceOverlay(service).also { overlay = it }
                renderer.show(bounds)
                activeAnchor = anchor
                rendered = true
            } finally {
                if (!rendered) onScreenChanged()
            }
            result
        }

    suspend fun complete(revision: Long): GuidanceState =
        withContext(Dispatchers.Main.immediate) {
            val service = checkNotNull(McpAccessibilityService.instance) { "Accessibility service disconnected" }
            if (state.value.goal == GuidanceSession.TEST_GOAL) {
                check(service.getRootNode()?.packageName?.toString() == "com.kakao.talk") {
                    "KakaoTalk is not the active app yet"
                }
            }
            session.complete(revision).also { clearHighlight() }
        }

    suspend fun cancel(): GuidanceState =
        withContext(Dispatchers.Main.immediate) {
            session.cancel().also { clearHighlight() }
        }

    suspend fun fail(instruction: String): GuidanceState =
        withContext(Dispatchers.Main.immediate) {
            session.fail(instruction).also { clearHighlight() }
        }

    /** Called only by the accessibility service on Main, excluding this app's overlay events. */
    fun onScreenChanged() {
        clearHighlight()
        session.screenChanged()
    }

    fun onContentChanged(windowId: Int) {
        if (state.value.phase == GuidancePhase.WAITING_USER) {
            val anchor = activeAnchor ?: return
            val keepHighlight = (windowId >= 0 && anchor.windowId != windowId) || anchor.isValid()
            if (keepHighlight) return
        }
        onScreenChanged()
    }

    fun onDisconnected() {
        clearHighlight()
        overlay = null
        session.disconnect()
    }

    private fun clearHighlight() {
        overlay?.clear()
        activeAnchor = null
    }
}
