package com.danielealbano.androidremotecontrolmcp.assistant.guide

/** The steps shown in this voice session (instruction -> outcome), passed to the planner with every request. */
internal class GuideHistory {
    private class Entry(
        val instruction: String,
        var outcome: String,
    )

    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun begin(instruction: String): Any {
        val entry = Entry(instruction, WAITING)
        entries += entry
        return entry
    }

    @Synchronized
    fun finish(
        handle: Any,
        outcome: String,
    ) {
        (handle as Entry).outcome = outcome
    }

    @Synchronized
    fun discard(handle: Any) {
        entries.remove(handle)
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun render(): String =
        entries
            .mapIndexed { index, entry -> "${index + 1}. '${entry.instruction}' -> ${entry.outcome}" }
            .joinToString("\n")
            .ifEmpty { NONE }

    private companion object {
        const val WAITING = "shown, waiting for the user"
        const val NONE = "(none)"
    }
}
