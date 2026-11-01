package com.danielealbano.androidremotecontrolmcp.data.model

/** On/off helper preferences stored with the assistant settings (see [AssistantConfig]). */
enum class HelperFlag {
    /** The first-run introduction was seen; false shows it again. Default false. */
    INTRO_SEEN,

    /** The helper reads its guidance and answers aloud. Default true. */
    SPEECH_ENABLED,
}
