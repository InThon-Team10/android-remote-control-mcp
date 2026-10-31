package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.accessibilityservice.AccessibilityService
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.danielealbano.androidremotecontrolmcp.assistant.voice.VOICE_TRACE
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AssistantOverlayHider
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.UserActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the [GuidanceController] state over other apps: an explanation panel, then a dimmed screen with a hole
 * over the target (four touchable blocker windows around it, so touches inside the hole reach the app), or, for
 * typing, just an outline and a caption so the keyboard stays usable. Hosted by the accessibility service.
 */
@Singleton
class GuidanceOverlayHost
    @Inject
    constructor(
        private val controller: GuidanceController,
        private val speaker: HelperSpeaker,
    ) : AccessibilityOverlayHost {
        private val hider = RefCountedOverlayHider(Dispatchers.Main.immediate) { windows?.setHidden(it) }
        private val planner = GuidanceScenePlanner()
        private val detector = HoleTouchDetector()
        private val handler = Handler(Looper.getMainLooper())
        private var service: AccessibilityService? = null
        private var windows: GuidanceWindowSet? = null
        private var renderer: GuidanceSceneRenderer? = null
        private var scope: CoroutineScope? = null
        private var shown: GuidanceScene? = null

        private val configurationCallbacks =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    shown = null
                    render(planner.plan(controller.state.value))
                }

                override fun onLowMemory() = Unit
            }

        override val overlayHider: AssistantOverlayHider get() = hider

        override fun attach(service: AccessibilityService) {
            if (this.service === service) return
            detach()
            this.service = service
            val windowSet =
                GuidanceWindowSet(service, service.getSystemService(WindowManager::class.java), ::onWindowTouch)
            windows = windowSet
            renderer =
                GuidanceSceneRenderer(
                    GuidanceDisplay(service),
                    windowSet,
                    detector,
                    controller,
                    onShowTarget = { render(planner.showTarget()) },
                )
            service.registerComponentCallbacks(configurationCallbacks)
            controller.setRendererAttached(true)
            scope =
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
                    scope.launch { controller.state.collect { render(planner.plan(it)) } }
                }
        }

        override fun detach() {
            val attached = service ?: return
            scope?.cancel()
            scope = null
            handler.removeCallbacksAndMessages(null)
            attached.unregisterComponentCallbacks(configurationCallbacks)
            windows?.clear()
            windows = null
            renderer = null
            detector.reset(emptySet())
            shown = null
            speaker.stop()
            planner.plan(null)
            service = null
            controller.setRendererAttached(false)
        }

        private fun onWindowTouch(
            windowId: Int,
            touch: WindowTouch,
            eventTime: Long,
        ) {
            if (!detector.record(windowId, touch, eventTime)) return
            handler.postDelayed({
                if (detector.decide(eventTime)) controller.onUserActivity(UserActivity.TARGET_TOUCHED)
            }, HoleTouchDetector.DECISION_DELAY_MS)
        }

        private fun render(scene: GuidanceScene) {
            val previous = shown
            val current = renderer
            if (current != null && scene != previous) {
                shown = scene
                windows?.clear()
                detector.reset(emptySet())
                current.show(scene)
                announce(previous, scene)
            }
        }

        private fun announce(
            previous: GuidanceScene?,
            scene: GuidanceScene,
        ) {
            val text = spokenText(scene)
            when {
                text == null -> {
                    Log.d(VOICE_TRACE, "guidance stops speech (no scene text)")
                    speaker.stop()
                }

                sceneKey(previous) != sceneKey(scene) -> {
                    Log.d(VOICE_TRACE, "guidance speaks instruction len=${text.length}")
                    speaker.speak(text)
                }
            }
        }

        private fun spokenText(scene: GuidanceScene): String? {
            val display = service?.let { GuidanceDisplay(it) }
            return when (scene) {
                GuidanceScene.Empty -> null
                is GuidanceScene.Explanation -> scene.text
                is GuidanceScene.Spotlight -> display?.captionText(scene.target)
                is GuidanceScene.TypeHint -> display?.captionText(scene.target)
                is GuidanceScene.SwipeHint -> display?.captionText(scene.target)
            }
        }

        private fun sceneKey(scene: GuidanceScene?): Pair<Long, Class<*>>? =
            when (scene) {
                null, GuidanceScene.Empty -> null
                is GuidanceScene.Explanation -> scene.stateId to scene.javaClass
                is GuidanceScene.Spotlight -> scene.stateId to scene.javaClass
                is GuidanceScene.TypeHint -> scene.stateId to scene.javaClass
                is GuidanceScene.SwipeHint -> scene.stateId to scene.javaClass
            }
    }
