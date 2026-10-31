package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject

/** The blocking highlight MCP call running in the background (at most one), and what `wait_for_user` awaits. */
internal class GuidePendingHighlight(
    private val connection: AssistantMcpConnection,
    private val controller: GuidanceController,
    private val history: GuideHistory,
    private val scope: CoroutineScope,
) {
    class Call(
        val tool: String,
        val arguments: JsonObject,
        val handle: Any,
        val result: Deferred<Result<CallToolResult>>,
    )

    private val lock = Any()
    private var current: Call? = null
    private var timedOut: Call? = null

    /** Starts the blocking call for [tool]; any previous one is superseded. */
    fun start(
        tool: String,
        arguments: JsonObject,
        handle: Any,
    ): Call =
        synchronized(lock) {
            current?.result?.cancel()
            timedOut = null
            launch(tool, arguments, handle).also { current = it }
        }

    /** The call ended before its step appeared: forget it. */
    fun dropEarly(call: Call) {
        synchronized(lock) { if (current === call) current = null }
    }

    /** Cancels the pending call and clears the guidance on screen. */
    fun abandon() {
        supersede()
        controller.cancel()
    }

    /** Cancels the pending call without touching the guidance (a new step is about to replace it). */
    fun supersede() {
        synchronized(lock) {
            current?.result?.cancel()
            current = null
            timedOut = null
        }
    }

    /** Awaits the pending highlight; after a timeout the highlight stays and waiting again re-issues the call. */
    suspend fun await(): String {
        val call = synchronized(lock) { current ?: relaunchTimedOut() }
        if (call == null) return GuideOutcomeTexts.NOTHING_HIGHLIGHTED
        val text = textOf(call.result.await())
        synchronized(lock) {
            if (current === call) current = null
            timedOut = call.takeIf { GuideOutcomeTexts.isTimedOut(text) }
        }
        return GuideOutcomeTexts.forVoice(text)
    }

    private fun relaunchTimedOut(): Call? {
        val previous = timedOut
        timedOut = null
        return previous
            ?.takeIf { controller.state.value != null }
            ?.let { launch(it.tool, it.arguments, it.handle).also { call -> current = call } }
    }

    private fun launch(
        tool: String,
        arguments: JsonObject,
        handle: Any,
    ): Call {
        val deferred =
            scope.async {
                connection.callTool(tool, arguments).also {
                    history.finish(handle, GuideOutcomeTexts.forHistory(textOf(it)))
                }
            }
        return Call(tool, arguments, handle, deferred)
    }

    companion object {
        fun textOf(result: Result<CallToolResult>): String? =
            result
                .getOrNull()
                ?.content
                ?.filterIsInstance<TextContent>()
                ?.joinToString("\n") { it.text }
    }
}
