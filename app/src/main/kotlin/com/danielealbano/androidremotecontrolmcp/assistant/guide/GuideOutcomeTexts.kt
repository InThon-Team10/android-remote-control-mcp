package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.mcp.tools.ElderToolGuidance

/** Maps the text a blocked highlight call ended with to the wording for the voice model and the planner history. */
internal object GuideOutcomeTexts {
    const val NOTHING_HIGHLIGHTED = "Nothing is highlighted right now."
    const val USER_DID_IT = "The user did it."
    const val SCREEN_CHANGED = "The screen changed before the user acted."
    const val TIMED_OUT = "Timed out: the user has not acted yet. The highlight stays on screen."
    const val CLOSED = "The user closed the guidance."
    const val REPLACED = "A newer step replaced the highlight."
    const val ENDED = "The highlight ended."
    private const val HISTORY_MAX = 120

    fun isTimedOut(text: String?) = text == ElderToolGuidance.TIMED_OUT_TEXT

    fun forVoice(text: String?): String =
        when (text) {
            ElderToolGuidance.USER_ACTED_TEXT -> USER_DID_IT
            ElderToolGuidance.SCREEN_CHANGED_TEXT -> SCREEN_CHANGED
            ElderToolGuidance.TIMED_OUT_TEXT -> TIMED_OUT
            ElderToolGuidance.CANCELLED_TEXT -> CLOSED
            ElderToolGuidance.SUPERSEDED_TEXT -> REPLACED
            else -> ENDED
        }

    fun forHistory(text: String?): String =
        when (text) {
            ElderToolGuidance.USER_ACTED_TEXT -> "the user did it"
            ElderToolGuidance.SCREEN_CHANGED_TEXT -> "the screen changed before the user acted"
            ElderToolGuidance.TIMED_OUT_TEXT -> "the user has not acted yet"
            ElderToolGuidance.CANCELLED_TEXT -> "the user closed the guidance"
            else -> text.orEmpty().take(HISTORY_MAX)
        }
}
