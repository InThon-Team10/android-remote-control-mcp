package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Reads only the current visible guidance caption, never an independently generated voice answer. */
@Singleton
class TextToSpeechHelperSpeaker
    @Inject
    constructor(
        private val settings: AssistantSettings,
    ) : HelperSpeaker,
        AccessibilityOverlayHost {
        private val main = Handler(Looper.getMainLooper())
        private var engine: TextToSpeech? = null
        private var scope: CoroutineScope? = null
        private var enabled = false
        private var ready = false
        private var generation = 0
        private var pending: String? = null
        private var accessibility: AccessibilityManager? = null

        override fun attach(service: AccessibilityService) {
            if (scope != null) return
            accessibility = service.getSystemService(AccessibilityManager::class.java)
            scope =
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { owner ->
                    owner.launch {
                        settings.config.collect { config ->
                            enabled = config.helperSpeechEnabled
                            if (!enabled) stop() else speakPending()
                        }
                    }
                }
            val attachedGeneration = ++generation
            engine =
                TextToSpeech(service.applicationContext) { status ->
                    main.post {
                        if (generation != attachedGeneration) return@post
                        val current = engine ?: return@post
                        if (status == TextToSpeech.SUCCESS) {
                            val result = current.setLanguage(Locale.KOREA)
                            // Keep the Korean voice's natural pitch instead of inheriting device-wide extremes.
                            current.setPitch(1.0f)
                            current.setSpeechRate(0.95f)
                            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                            speakPending()
                        }
                    }
                }
        }

        override fun speak(text: String) {
            main.post {
                pending = text.takeIf { it.isNotBlank() }
                engine?.stop()
                speakPending()
            }
        }

        private fun speakPending() {
            val text = pending ?: return
            if (!speechAllowed(enabled, ready, text)) return
            pending = null
            if (accessibility?.isTouchExplorationEnabled == true) return
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guidance-caption")
        }

        override fun stop() {
            main.post {
                pending = null
                engine?.stop()
            }
        }

        override fun detach() {
            generation++
            main.removeCallbacksAndMessages(null)
            pending = null
            scope?.cancel()
            scope = null
            ready = false
            enabled = false
            engine?.stop()
            engine?.shutdown()
            engine = null
            accessibility = null
        }
    }

internal fun speechAllowed(
    enabled: Boolean,
    ready: Boolean,
    text: String,
): Boolean = enabled && ready && text.isNotBlank()
