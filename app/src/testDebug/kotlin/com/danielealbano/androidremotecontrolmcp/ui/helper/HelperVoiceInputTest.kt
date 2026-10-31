package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HelperVoiceInputTest {
    private val context = mockk<Context>()
    private val engine = mockk<SpeechRecognizer>(relaxed = true)
    private val listener = slot<RecognitionListener>()
    private var text = "기존 질문"
    private var listening = false
    private var recognizing = false
    private lateinit var input: HelperVoiceInput

    @BeforeEach fun setup() {
        mockkStatic(SpeechRecognizer::class, Looper::class)
        mockkConstructor(Handler::class)
        every { Looper.getMainLooper() } returns mockk()
        every { anyConstructed<Handler>().removeCallbacks(any()) } returns Unit
        every { anyConstructed<Handler>().postDelayed(any(), any<Long>()) } returns true
        every { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) } returns true
        every { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) } returns engine
        every { engine.setRecognitionListener(capture(listener)) } returns Unit
        every { context.getString(any()) } returns "상태"
        input =
            HelperVoiceInput(context, { text = it }) { active, pending, _ ->
                listening = active
                recognizing = pending
            }
    }

    @AfterEach fun cleanup() = unmockkAll()

    private fun result(text: String): Bundle =
        mockk<Bundle>().also {
            every { it.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) } returns arrayListOf(text)
        }

    @Test fun `partial replaces only current utterance and endpoint resets mic before final`() {
        input.start(text)
        listener.captured.onPartialResults(result("검색"))
        assertEquals("기존 질문 검색", text)
        listener.captured.onPartialResults(result("검색하고 싶어요"))
        assertEquals("기존 질문 검색하고 싶어요", text)
        listener.captured.onEndOfSpeech()
        assertFalse(listening)
        assertTrue(recognizing)
        listener.captured.onResults(result("검색해 주세요"))
        assertEquals("기존 질문 검색해 주세요", text)
        assertFalse(listening)
        assertFalse(recognizing)
    }

    @Test fun `explicit stop resets mic and error preserves partial text`() {
        input.start(text)
        listener.captured.onPartialResults(result("검색"))
        input.stop()
        verify { engine.stopListening() }
        listener.captured.onReadyForSpeech(null)
        assertFalse(listening)
        listener.captured.onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals("기존 질문 검색", text)
        assertFalse(recognizing)
    }

    @Test fun `cancel ignores late results and errors from old session`() {
        input.start(text)
        val old = listener.captured
        input.cancel()
        input.start("새 질문")
        old.onResults(result("이전 내용"))
        old.onError(SpeechRecognizer.ERROR_CLIENT)
        assertEquals("기존 질문", text)
        assertTrue(listening)
        listener.captured.onResults(result("새 내용"))
        assertEquals("새 질문 새 내용", text)
    }

    @Test fun `standard service is used when on-device recognition is absent`() {
        every { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) } returns false
        every { SpeechRecognizer.isRecognitionAvailable(context) } returns true
        every { SpeechRecognizer.createSpeechRecognizer(context) } returns engine
        input.start(text)
        verify { SpeechRecognizer.createSpeechRecognizer(context) }
        assertTrue(listening)
    }
}
