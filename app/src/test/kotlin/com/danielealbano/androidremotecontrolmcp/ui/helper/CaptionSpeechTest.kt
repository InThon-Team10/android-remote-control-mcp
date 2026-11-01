package com.danielealbano.androidremotecontrolmcp.ui.helper

import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.Test

class CaptionSpeechTest {
    private val speaker = mockk<HelperSpeaker>(relaxed = true)
    private val speech = CaptionSpeech(speaker)

    @Test
    fun `reads exactly the visible caption once despite keyboard relayout`() {
        val caption = GuidanceCaption("이 톡방을 눌러 주세요", "선택한 방이 열려요")
        speech.show(1, caption)
        speech.show(1, caption)
        verify(exactly = 1) { speaker.speak("이 톡방을 눌러 주세요\n선택한 방이 열려요") }
        verify(exactly = 1) { speaker.speak(any()) }
    }

    @Test
    fun `new caption replaces narration and removing it stops speech`() {
        speech.show(1, GuidanceCaption("첫 안내"))
        speech.show(2, GuidanceCaption("다음 안내"))
        speech.clear()
        verifySequence {
            speaker.speak("첫 안내")
            speaker.speak("다음 안내")
            speaker.stop()
        }
    }

    @Test
    fun `showing again after close can read the same caption`() {
        val caption = GuidanceCaption("말풍선 내용만")
        speech.show(1, caption)
        speech.clear()
        speech.show(1, caption)
        verify(exactly = 2) { speaker.speak("말풍선 내용만") }
    }
}
