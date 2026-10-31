package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError

/** Provider-independent entry point of the assistant: one user input per [run], history kept until [reset]. */
interface AssistantAgent {
    /**
     * Runs one user input to completion with the provider history kept internally until [reset].
     * Cooperative with cancellation: on cancel the incomplete turn is dropped and the exception rethrown.
     */
    suspend fun run(
        input: AgentInput,
        listener: AgentListener,
    ): AgentOutcome

    /** Drops the whole provider history. */
    fun reset()
}

sealed interface AgentInput {
    data class Text(
        val text: String,
    ) : AgentInput
}

interface AgentListener {
    fun onAssistantText(text: String)

    /** Called before every tool call that runs; returns once the chat window is hidden (or after 2 s). */
    suspend fun beforeToolCall()

    /** Called only for tool calls that actually run (never for a denied confirmation). */
    fun onToolCallStarted(
        callId: String,
        toolName: String,
    )

    fun onToolCallFinished(
        callId: String,
        toolName: String,
        isError: Boolean,
    )

    /** Suspends until the user allows (true) or denies (false) [toolName]. */
    suspend fun confirm(
        callId: String,
        toolName: String,
    ): Boolean
}

sealed interface AgentOutcome {
    data object Completed : AgentOutcome

    data class StepLimitReached(
        val limit: Int,
    ) : AgentOutcome

    data class Failed(
        val error: AssistantError,
    ) : AgentOutcome
}
