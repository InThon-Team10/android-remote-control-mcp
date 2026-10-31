package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.danielealbano.androidremotecontrolmcp.R

/** One explicit dictation session; stale recognizer callbacks never replace a newer draft. */
internal class HelperVoiceInput(
    private val context: Context,
    private val onTranscript: (String) -> Unit,
    private val onState: (listening: Boolean, recognizing: Boolean, message: String?) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var awaitingResults = false
    private val handler = Handler(Looper.getMainLooper())
    private val resultTimeout = Runnable { finish(R.string.helper_voice_timeout) }

    fun start(draft: String) {
        cancel()
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context) && !SpeechRecognizer.isRecognitionAvailable(context)) {
            onState(false, false, context.getString(R.string.helper_voice_unavailable))
            return
        }
        val token = generation
        val prefix = draft.trimEnd().let { if (it.isEmpty()) "" else "$it " }
        try {
            val engine =
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            recognizer = engine
            engine.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        if (token == generation && !awaitingResults) {
                            handler.removeCallbacks(resultTimeout)
                            onState(true, true, context.getString(R.string.helper_voice_listening))
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
                        if (token != generation) return
                        finish(
                            when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.helper_voice_permission_denied

                                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.helper_voice_no_match

                                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                                -> R.string.helper_voice_language_unavailable

                                else -> R.string.helper_voice_error
                            },
                        )
                    }
                },
            )
            onState(true, true, context.getString(R.string.helper_voice_listening))
            handler.postDelayed(resultTimeout, RESULT_TIMEOUT_MS)
            engine.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                },
            )
        } catch (_: RuntimeException) {
            finish(R.string.helper_voice_error)
        }
    }

    fun stop() {
        if (recognizer == null) return
        awaitResults()
        try {
            recognizer?.stopListening()
        } catch (_: RuntimeException) {
            finish(R.string.helper_voice_error)
        }
    }

    private fun awaitResults() {
        awaitingResults = true
        onState(false, true, context.getString(R.string.helper_voice_processing))
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

    private fun finish(message: Int) {
        cancel()
        onState(false, false, context.getString(message))
    }

    fun cancel() {
        generation++
        handler.removeCallbacks(resultTimeout)
        awaitingResults = false
        val engine = recognizer
        recognizer = null
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
        onState(false, false, null)
    }

    private companion object {
        const val RESULT_TIMEOUT_MS = 8_000L
    }
}
