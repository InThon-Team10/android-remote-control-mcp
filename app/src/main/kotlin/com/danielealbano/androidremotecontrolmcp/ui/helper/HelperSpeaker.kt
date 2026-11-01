package com.danielealbano.androidremotecontrolmcp.ui.helper

/**
 * Reads helper text aloud. Implementations do nothing while `AssistantConfig.helperSpeechEnabled` is off or no
 * text-to-speech engine is available. Callable from any thread.
 */
interface HelperSpeaker {
    /** Speaks [text], replacing anything still being spoken. */
    fun speak(text: String)

    fun stop()
}
