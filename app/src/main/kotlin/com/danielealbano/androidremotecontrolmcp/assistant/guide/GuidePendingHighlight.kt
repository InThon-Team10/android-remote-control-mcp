package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

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
        val call = synchronized(lock) { current ?: relaunchTimedOut() } ?: return GuideOutcomeTexts.NOTHING_HIGHLIGHTED
        val replaced = awaitText(call)
        return if (replaced == null) {
            GuideOutcomeTexts.REPLACED
        } else {
            val text = replaced.value
            synchronized(lock) {
                if (current === call) current = null
                timedOut = call.takeIf { GuideOutcomeTexts.isTimedOut(text) }
            }
            GuideOutcomeTexts.forVoice(text)
        }
    }

    private class Text(
        val value: String?,
    )

    /** The call's text, or null when a newer step cancelled it while the waiter itself is still active. */
    private suspend fun awaitText(call: Call): Text? {
        val outcome = runCatching { call.result.await() }
        val failure = outcome.exceptionOrNull()
        if (failure is CancellationException) currentCoroutineContext().ensureActive()
        return if (failure is CancellationException) null else Text(textOf(outcome.getOrThrow()))
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
