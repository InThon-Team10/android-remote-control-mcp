package com.danielealbano.androidremotecontrolmcp.ui.helper.practice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.danielealbano.androidremotecontrolmcp.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** What the dictation shows: [listening] while the microphone is open, [recognizing] until the text is final. */
internal data class PracticeDictationState(
    val listening: Boolean = false,
    val recognizing: Boolean = false,
    val message: String? = null,
) {
    val busy: Boolean get() = listening || recognizing
}

/**
 * Dictation for the practice only: nothing is sent to the AI. It must never run while the live conversation holds the
 * microphone.
 */
internal interface PracticeDictation {
    val state: StateFlow<PracticeDictationState>

    /** False when the device has no on-device recognizer: the practice then offers typing only. */
    val available: Boolean

    fun start(draft: String)

    fun stop()

    fun cancel()
}

/**
 * Dictation through the on-device recognizer only (the practice runs before the user consented to send anything, so
 * there is no fallback to a network recognizer). Partials replace the current utterance, a generation token ignores
 * stale callbacks and a result that does not come in time ends the session. [isBlocked] is true while the live
 * conversation is active; starting is then refused.
 */
internal class OnDevicePracticeDictation(
    private val context: Context,
    private val onTranscript: (String) -> Unit,
    private val isBlocked: () -> Boolean = { false },
) : PracticeDictation {
    private val mutableState = MutableStateFlow(PracticeDictationState())
    override val state: StateFlow<PracticeDictationState> = mutableState

    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var awaitingResults = false
    private val handler = Handler(Looper.getMainLooper())
    private val resultTimeout = Runnable { finish(R.string.helper_voice_timeout) }

    override val available: Boolean get() = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun start(draft: String) {
        cancel()
        if (isBlocked()) return
        if (!available) {
            publish(false, false, R.string.helper_voice_unavailable)
            return
        }
        try {
            val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = engine
            val prefix = draft.trimEnd().let { if (it.isEmpty()) "" else "$it " }
            engine.setRecognitionListener(listener(generation, prefix))
            publish(true, true, R.string.helper_voice_listening)
            handler.postDelayed(resultTimeout, RESULT_TIMEOUT_MS)
            engine.startListening(recognizerIntent())
        } catch (_: RuntimeException) {
            finish(R.string.helper_voice_error)
        }
    }

    override fun stop() {
        if (recognizer == null) return
        awaitResults()
        try {
            recognizer?.stopListening()
        } catch (_: RuntimeException) {
            finish(R.string.helper_voice_error)
        }
    }

    override fun cancel() {
        generation++
        handler.removeCallbacks(resultTimeout)
        awaitingResults = false
        val engine = recognizer
        recognizer = null
        release(engine)
        mutableState.value = PracticeDictationState()
    }

    private fun listener(
        token: Int,
        prefix: String,
    ) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (token == generation && !awaitingResults) {
                handler.removeCallbacks(resultTimeout)
                publish(true, true, R.string.helper_voice_listening)
            }
        }

        override fun onBeginningOfSpeech() {
            if (token == generation && !awaitingResults) handler.removeCallbacks(resultTimeout)
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEvent(
            eventType: Int,
            params: Bundle?,
        ) = Unit

        override fun onEndOfSpeech() {
            if (token == generation) awaitResults()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (token == generation) updateTranscript(partialResults, prefix)
        }

        override fun onResults(results: Bundle?) {
            if (token != generation) return
            val received = updateTranscript(results, prefix)
            finish(if (received) R.string.helper_voice_done else R.string.helper_voice_no_match)
        }

        override fun onError(error: Int) {
            if (token == generation) finish(errorMessage(error))
        }
    }

    private fun awaitResults() {
        awaitingResults = true
        publish(false, true, R.string.helper_voice_processing)
        handler.removeCallbacks(resultTimeout)
        handler.postDelayed(resultTimeout, RESULT_TIMEOUT_MS)
    }

    private fun updateTranscript(
        results: Bundle?,
        prefix: String,
    ): Boolean {
        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
        if (text.isNullOrEmpty()) return false
        onTranscript(prefix + text)
        return true
    }

    private fun finish(
        @StringRes message: Int,
    ) {
        cancel()
        publish(false, false, message)
    }

    private fun publish(
        listening: Boolean,
        recognizing: Boolean,
        @StringRes message: Int,
    ) = mutableState.update { PracticeDictationState(listening, recognizing, context.getString(message)) }

    private fun release(engine: SpeechRecognizer?) {
        try {
            engine?.cancel()
        } catch (_: RuntimeException) {
            // A disconnected system recognition service may reject cancellation.
        } finally {
            try {
                engine?.destroy()
            } catch (_: RuntimeException) {
                // State must still reset when the provider has already died.
            }
        }
    }

    private fun recognizerIntent() =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    @StringRes
    private fun errorMessage(error: Int): Int =
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.helper_voice_permission_denied

            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.helper_voice_no_match

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
            -> R.string.helper_voice_language_unavailable

            else -> R.string.helper_voice_error
        }

    private companion object {
        const val RESULT_TIMEOUT_MS = 8_000L
        const val LANGUAGE = "ko-KR"
    }
}
