package com.danielealbano.androidremotecontrolmcp.ui.helper

/** Speech accepts only the same caption object rendered by guidance, never conversation or progress text. */
internal class CaptionSpeech(
    private val speaker: HelperSpeaker,
) {
    private var current: Pair<Long, GuidanceCaption>? = null

    fun show(
        stepId: Long,
        caption: GuidanceCaption,
    ) {
        val next = stepId to caption
        if (current == next) return
        current = next
        speaker.speak(caption.spokenText)
    }

    fun clear() {
        current = null
        speaker.stop()
    }
}
