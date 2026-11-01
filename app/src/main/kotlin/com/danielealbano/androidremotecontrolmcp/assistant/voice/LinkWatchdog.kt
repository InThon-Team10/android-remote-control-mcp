package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Realtime connection has no ping and the server is silent while the user is: every [HEARTBEAT_MS] an idempotent
 * `session.update` is sent, and [MAX_MISSED_BEATS] beats without any server event mean the link is dead.
 */
internal object LinkWatchdog {
    const val HEARTBEAT_MS = 20_000L
    const val MAX_MISSED_BEATS = 3
    private const val TAG = "MCP:LiveVoice"

    /** [heard] is set by every inbound event. */
    suspend fun run(
        heard: AtomicBoolean,
        send: (JsonObject) -> Unit,
        events: LiveEvents,
    ) {
        var missed = 0
        while (missed < MAX_MISSED_BEATS) {
            delay(HEARTBEAT_MS)
            missed = if (heard.getAndSet(false)) 0 else missed + 1
            if (missed < MAX_MISSED_BEATS) send(RealtimeProtocol.sessionUpdate())
        }
        Logger.w(TAG, "Live voice link silent, ending the conversation")
        events.end(AgentOutcome.Failed(AssistantError.ModelUnavailable))
    }
}
