package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.accessibilityservice.AccessibilityService
import android.speech.tts.TextToSpeech
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.VOICE_TRACE
import com.danielealbano.androidremotecontrolmcp.assistant.voice.isActive
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HelperSpeaker] backed by the platform text-to-speech engine, created when the accessibility service attaches
 * and shut down when it detaches. Silent while `helperSpeechEnabled` is off or the engine is not ready.
 */
@Singleton
class TextToSpeechHelperSpeaker
    @Inject
    constructor(
        private val assistantSettings: AssistantSettings,
        private val liveVoice: LiveVoiceSession,
    ) : HelperSpeaker,
        AccessibilityOverlayHost {
        private val engine = AtomicReference<TextToSpeech?>(null)
        private val ready = AtomicBoolean(false)
        private val enabled = AtomicBoolean(false)
        private val liveConversation = AtomicBoolean(false)
        private var scope: CoroutineScope? = null

        override fun attach(service: AccessibilityService) {
            if (engine.get() != null) return
            scope =
                CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
                    scope.launch { assistantSettings.config.map { it.helperSpeechEnabled }.collect(enabled::set) }
                    // A live conversation speaks with Gemini's own voice; this engine's output is not echo-cancelled,
                    // so speaking over it makes the live model hear itself, interrupt its reply and answer the echo.
                    scope.launch {
                        liveVoice.state.map { it.isActive() }.distinctUntilChanged().collect { active ->
                            liveConversation.set(active)
                            if (active) engine.get()?.stop()
                        }
                    }
                }
            val created = TextToSpeech(service.applicationContext) { status -> onEngineInit(status) }
            created.setOnUtteranceProgressListener(TtsTrace)
            engine.set(created)
        }

        override fun detach() {
            val current = engine.getAndSet(null) ?: return
            ready.set(false)
            scope?.cancel()
            scope = null
            enabled.set(false)
            liveConversation.set(false)
            current.stop()
            current.shutdown()
        }

        override fun speak(text: String) {
            Log.d(VOICE_TRACE, "tts speak len=${text.length} enabled=${enabled.get()} ready=${ready.get()}")
            if (!speechAllowed(enabled.get(), ready.get(), liveConversation.get(), text)) return
            // Queued so a guidance instruction never cuts off the answer being read; stop() clears the queue.
            engine.get()?.speak(text, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
        }

        override fun stop() {
            Log.d(VOICE_TRACE, "tts stop")
            engine.get()?.stop()
        }

        private fun onEngineInit(status: Int) {
            val current = engine.get()
            if (status != TextToSpeech.SUCCESS || current == null) {
                Log.w(TAG, "Text-to-speech engine unavailable (status $status)")
                return
            }
            val korean = supports(current.setLanguage(Locale.KOREAN))
            ready.set(korean || supports(current.setLanguage(Locale.getDefault())))
            Log.d(VOICE_TRACE, "tts ready=${ready.get()} korean=$korean engine=${current.defaultEngine}")
        }

        private fun supports(result: Int): Boolean {
            val unsupported = result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED
            return !unsupported
        }

        private companion object {
            const val TAG = "MCP:HelperSpeaker"
            const val UTTERANCE_ID = "helper-guidance"
        }
    }

/** Content-free trace of the text-to-speech utterance lifecycle. */
private object TtsTrace : android.speech.tts.UtteranceProgressListener() {
    override fun onStart(utteranceId: String?) {
        Log.d(VOICE_TRACE, "tts utterance started")
    }

    override fun onDone(utteranceId: String?) {
        Log.d(VOICE_TRACE, "tts utterance done")
    }

    override fun onStop(
        utteranceId: String?,
        interrupted: Boolean,
    ) {
        Log.d(VOICE_TRACE, "tts utterance stopped interrupted=$interrupted")
    }

    @Deprecated("Deprecated in Java")
    override fun onError(utteranceId: String?) {
        Log.d(VOICE_TRACE, "tts utterance error")
    }

    override fun onError(
        utteranceId: String?,
        errorCode: Int,
    ) {
        Log.d(VOICE_TRACE, "tts utterance error code=$errorCode")
    }
}

/**
 * Helper speech plays only when switched on, the engine is ready, no live conversation is running and there is
 * text to say.
 */
internal fun speechAllowed(
    enabled: Boolean,
    ready: Boolean,
    liveConversation: Boolean,
    text: String,
): Boolean = enabled && ready && !liveConversation && text.isNotBlank()
