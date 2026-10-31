package com.danielealbano.androidremotecontrolmcp.data.model

/**
 * Persisted assistant configuration. The Gemini API key is deliberately NOT part of this class: only
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
        /** Pinned (owner decision): Google's alias that always points to the newest Gemini Flash. */
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val LIVE_MODEL = "gemini-3.8-live"
        const val DEFAULT_STEP_LIMIT = 30
        const val MIN_STEP_LIMIT = 1
        const val MAX_STEP_LIMIT = 100

        /** The text assistant uses only the pinned model; live voice uses [LIVE_MODEL] (no Flash model is live). */
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
