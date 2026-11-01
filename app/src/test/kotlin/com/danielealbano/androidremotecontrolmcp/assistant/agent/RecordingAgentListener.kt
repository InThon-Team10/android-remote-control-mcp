package com.danielealbano.androidremotecontrolmcp.assistant.agent

/** Records every [AgentListener] callback in order; [confirmAnswer] decides confirmations. */
class RecordingAgentListener(
    var confirmAnswer: (toolName: String) -> Boolean = { true },
) : AgentListener {
    val events = mutableListOf<String>()

    override fun onAssistantText(text: String) {
        events += "text:$text"
    }

    override suspend fun beforeToolCall() {
        events += "before"
    }

    override fun onToolCallStarted(
        callId: String,
        toolName: String,
    ) {
        events += "started:$toolName"
    }

    override fun onToolCallFinished(
        callId: String,
        toolName: String,
        isError: Boolean,
    ) {
        events += "finished:$toolName:$isError"
    }

    override suspend fun confirm(
        callId: String,
        toolName: String,
    ): Boolean {
        events += "confirm:$toolName"
        return confirmAnswer(toolName)
    }
}
