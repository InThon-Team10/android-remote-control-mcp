package com.danielealbano.androidremotecontrolmcp.assistant.voice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EchoGate")
class EchoGateTest {
    private var pendingMs = 0L
    private var nowMs = 1_000L
    private val gate = EchoGate({ pendingMs }, { nowMs }, tailMs = 300)

    @Test
    fun `the microphone is open before the assistant has spoken`() {
        assertTrue(gate.allows())
    }

    @Test
    fun `the microphone is closed while the assistant's voice is playing`() {
        pendingMs = 400

        assertFalse(gate.allows())
    }

    @Test
    fun `the microphone stays closed for the tail after the voice drains`() {
        pendingMs = 400
        gate.allows()
        pendingMs = 0
        nowMs += 299

        assertFalse(gate.allows())
    }

    @Test
    fun `the microphone opens again once the tail has passed`() {
        pendingMs = 400
        gate.allows()
        pendingMs = 0
        nowMs += 300

        assertTrue(gate.allows())
    }

    @Test
    fun `a new reply closes the microphone again`() {
        pendingMs = 400
        gate.allows()
        pendingMs = 0
        nowMs += 1_000
        gate.allows()
        pendingMs = 200

        assertFalse(gate.allows())
    }
}
