package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideOutcomeTexts
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideStepResult
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs the voice model's tools and drives the guidance steps. `show_next_step` plans and shows a step; after every
 * shown step the app itself waits for the user's action, because on the phone the voice model did not reliably call
 * `wait_for_user` and the user's action went unnoticed. When the user acts, the next step towards the current goal is
 * planned at once and handed over: as the answer of a pending `wait_for_user` call ([respond]), otherwise as an app
 * note the model reads out ([push]). [fail] gets any failure.
 */
internal class RealtimeFunctionCalls(
    private val guide: GuideSteps,
    private val listener: AgentListener,
    private val scope: CoroutineScope,
    private val respond: (callId: String, output: String) -> Unit,
    private val push: (note: String) -> Unit,
    private val fail: (Throwable) -> Unit,
) {
    private val lock = Any()
    private var goal: String? = null
    private var step = 0L
    private var watching = false
    private var waitingCall: String? = null

    /** [item] is a `function_call` output item; one without a call id is ignored. */
    fun start(item: JsonObject) {
        val callId = item.string("call_id") ?: return
        val name = item.string("name").orEmpty()
        when (name) {
            RealtimeProtocol.WAIT_FOR_USER -> {
                registerWait(callId)
            }

            RealtimeProtocol.SHOW_NEXT_STEP -> {
                val arguments = item.string("arguments").orEmpty()
                launch { respond(callId, showNextStep(arguments)) }
            }

            else -> {
                respond(callId, "Unknown tool: $name")
            }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch {
            val failure = runCatching { block() }.exceptionOrNull() ?: return@launch
            if (failure is CancellationException || failure is Error) throw failure
            fail(failure)
        }
    }

    private suspend fun showNextStep(arguments: String): String {
        val goal =
            runCatching { Json.parseToJsonElement(arguments).jsonObject.string("goal") }.getOrNull()?.takeIf {
                it.isNotBlank()
            } ?: return MISSING_GOAL
        synchronized(lock) { this.goal = goal }
        val result = guide.showNextStep(goal, listener)
        afterStep(goal, result)
        return result.toJson().toString()
    }

    /** A step was planned for [goal]: a reached goal ends it, a shown highlight is watched. */
    private fun afterStep(
        goal: String,
        result: GuideStepResult,
    ) {
        if (result.kind == DONE) synchronized(lock) { if (this.goal == goal) this.goal = null }
        if (result.showing) watch()
    }

    private fun watch() {
        val id =
            synchronized(lock) {
                watching = true
                ++step
            }
        launch { onOutcome(id, guide.waitForUser()) }
    }

    private fun registerWait(callId: String) {
        val answerNow =
            synchronized(lock) {
                if (watching) {
                    waitingCall = callId
                    null
                } else {
                    GuideOutcomeTexts.NOTHING_HIGHLIGHTED
                }
            }
        answerNow?.let { respond(callId, it) }
    }

    private suspend fun onOutcome(
        id: Long,
        outcome: String,
    ) {
        val current =
            synchronized(lock) {
                if (id != step || outcome == GuideOutcomeTexts.REPLACED) return
                watching = false
                goal
            }
        val continues = current != null && outcome in CONTINUING_OUTCOMES
        val next = if (continues && current != null) guide.showNextStep(current, listener) else null
        when {
            next != null && current != null -> afterStep(current, next)
            outcome == GuideOutcomeTexts.TIMED_OUT -> watch()
        }
        val call = synchronized(lock) { waitingCall.also { waitingCall = null } }
        if (call != null) {
            respond(call, answer(outcome, next))
        } else {
            note(outcome, next)?.let(push)
        }
    }

    private fun answer(
        outcome: String,
        next: GuideStepResult?,
    ): String =
        next?.let {
            buildJsonObject {
                put("result", outcome)
                put("next", it.toJson())
            }.toString()
        } ?: outcome

    private fun note(
        outcome: String,
        next: GuideStepResult?,
    ): String? =
        when {
            next != null && next.kind == DONE -> "앱 알림: 사용자가 목표를 마쳤어요. 짧게 축하하며 말하세요: \"${next.say}\""
            next != null && next.showing -> "앱 알림: 사용자가 방금 단계를 마쳤고 다음 안내가 화면에 있어요. 그대로 말하세요: \"${next.say}\""
            next != null -> "앱 알림: 그대로 말하세요: \"${next.say}\""
            outcome == GuideOutcomeTexts.TIMED_OUT -> "앱 알림: 사용자가 아직 하지 않았어요. 안내는 화면에 있어요. 짧게 다시 안내하세요."
            outcome == GuideOutcomeTexts.CLOSED -> "앱 알림: 사용자가 안내를 닫았어요. 무엇을 하고 싶은지 짧게 물어보세요."
            outcome in CONTINUING_OUTCOMES -> "앱 알림: 사용자가 안내한 단계를 했어요."
            else -> null
        }

    private companion object {
        const val DONE = "done"
        const val MISSING_GOAL = "The goal is missing: call show_next_step again with the goal."
        val CONTINUING_OUTCOMES = setOf(GuideOutcomeTexts.USER_DID_IT, GuideOutcomeTexts.SCREEN_CHANGED)
    }
}
