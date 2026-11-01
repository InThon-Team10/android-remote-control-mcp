package com.danielealbano.androidremotecontrolmcp.data.model

/**
 * Persisted assistant configuration. The OpenAI API key is deliberately NOT part of this class: only
 * [apiKeyConfigured] is exposed so the key cannot leak through state, logs or `toString()`.
 * [builtInApiKeyInUse] is true when no key is saved and the built-in key embedded at build time is used instead.
 */
data class AssistantConfig(
    val enabled: Boolean = false,
    val voiceEnabled: Boolean = false,
    val model: String = DEFAULT_MODEL,
    val stepLimit: Int = DEFAULT_STEP_LIMIT,
    val privacyNoticeAccepted: Boolean = false,
    val apiKeyConfigured: Boolean = false,
    val helperIntroSeen: Boolean = false,
    val helperSpeechEnabled: Boolean = true,
    val builtInApiKeyInUse: Boolean = false,
) {
    companion object {
        /** Pinned (owner decision): the text planner's model on the Responses API, run with low reasoning effort. */
        const val DEFAULT_MODEL = "gpt-6-luna"

        /** Pinned (owner decision): the live voice model on the Realtime API. */
        const val LIVE_MODEL = "gpt-realtime-2.1"
        const val DEFAULT_STEP_LIMIT = 30
        const val MIN_STEP_LIMIT = 1
        const val MAX_STEP_LIMIT = 100

        /**
         * The text assistant uses only the pinned [DEFAULT_MODEL]; a stored model outside this list (for example one
         * saved for the previous AI provider) reads as [DEFAULT_MODEL]. Live voice uses [LIVE_MODEL].
         */
        val MODELS: List<String> = listOf(DEFAULT_MODEL)

        /** Tools (un-prefixed names) that require an Allow/Deny confirmation in the chat before running. */
        val CONFIRMATION_TOOLS: Set<String> =
            setOf(
                "close_app",
                "share_file_via_web",
                "take_camera_photo",
                "save_camera_photo",
                "save_camera_video",
                "set_clipboard",
            )

        /** Pure validation: [Result.success] with [value], or failure with an [IllegalArgumentException]. */
        fun validateStepLimit(value: Int): Result<Int> =
            if (value in MIN_STEP_LIMIT..MAX_STEP_LIMIT) {
                Result.success(value)
            } else {
                Result.failure(
                    IllegalArgumentException("Step limit must be between $MIN_STEP_LIMIT and $MAX_STEP_LIMIT"),
                )
            }
    }
}
