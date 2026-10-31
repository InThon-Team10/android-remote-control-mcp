package com.danielealbano.androidremotecontrolmcp.assistant.session

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentInput
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AssistantAgent

class FakeAssistantAgent : AssistantAgent {
    var script: suspend (AgentInput, AgentListener) -> AgentOutcome = { _, _ -> AgentOutcome.Completed }
    val inputs = mutableListOf<AgentInput>()
    var resetCount = 0

    override suspend fun run(
        input: AgentInput,
        listener: AgentListener,
    ): AgentOutcome {
        inputs += input
        return script(input, listener)
    }

    override fun reset() {
        resetCount++
    }
}
