package com.danielealbano.androidremotecontrolmcp.mcp.tools

/**
 * Fans every tool-call transition out to [activity] (always) and [visual]. Each target is isolated with `runCatching`
 * so one failing target never blocks the other or the tool call. [setEnabled] only reaches [visual].
 */
class CompositeToolCallIndicator(
    private val visual: ToolCallIndicator,
    private val activity: ToolCallIndicator,
) : ToolCallIndicator {
    override fun onToolCallStarted(toolName: String) {
        runCatching { activity.onToolCallStarted(toolName) }
        runCatching { visual.onToolCallStarted(toolName) }
    }

    override fun onToolCallFinished(toolName: String) {
        runCatching { activity.onToolCallFinished(toolName) }
        runCatching { visual.onToolCallFinished(toolName) }
    }

    override fun setEnabled(enabled: Boolean) {
        runCatching { visual.setEnabled(enabled) }
    }
}
