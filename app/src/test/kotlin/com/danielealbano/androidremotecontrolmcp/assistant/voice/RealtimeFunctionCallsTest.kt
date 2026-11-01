package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideOutcomeTexts
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideStepResult
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("RealtimeFunctionCalls")
class RealtimeFunctionCallsTest {
    private val guide = mockk<GuideSteps>()
    private val listener = mockk<AgentListener>(relaxed = true)
    private val answers = mutableListOf<Pair<String, String>>()
    private val notes = mutableListOf<String>()
    private val failures = mutableListOf<Throwable>()
    private val outcomes = ArrayDeque<CompletableDeferred<String>>()

    init {
        coEvery { guide.waitForUser() } coAnswers { outcomes.removeFirst().await() }
    }

    private fun TestScope.calls(): RealtimeFunctionCalls {
        val respond: (String, String) -> Unit = { id, output -> answers += id to output }
        val push: (String) -> Unit = { notes += it }
        return RealtimeFunctionCalls(guide, listener, backgroundScope, respond, push) { failures += it }
    }

    private fun call(
        id: String,
        name: String,
        arguments: String = "{}",
    ): JsonObject =
        buildJsonObject {
            put("type", "function_call")
            put("call_id", id)
            put("name", name)
            put("arguments", arguments)
        }

    private fun nextOutcome(): CompletableDeferred<String> = CompletableDeferred<String>().also(outcomes::addLast)

    private fun TestScope.show(calls: RealtimeFunctionCalls): CompletableDeferred<String> {
        val outcome = nextOutcome()
        calls.start(call("s1", RealtimeProtocol.SHOW_NEXT_STEP, """{"goal":"$GOAL"}"""))
        runCurrent()
        return outcome
    }

    @Test
    fun `a shown step is answered and the next one reaches a pending wait_for_user when the user acts`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returnsMany
                listOf(GuideStepResult(FIRST, showing = true), GuideStepResult(SECOND, showing = true))
            val calls = calls()
            val outcome = show(calls)
            calls.start(call("w1", RealtimeProtocol.WAIT_FOR_USER))
            nextOutcome()

            outcome.complete(GuideOutcomeTexts.USER_DID_IT)
            runCurrent()

            assertEquals("s1", answers[0].first)
            assertEquals("w1", answers[1].first)
            val answer = Json.parseToJsonElement(answers[1].second).jsonObject
            assertEquals(GuideOutcomeTexts.USER_DID_IT, answer["result"]!!.jsonPrimitive.content)
            assertEquals(SECOND, answer["next"]!!.jsonObject["say"]!!.jsonPrimitive.content)
            assertTrue(notes.isEmpty() && failures.isEmpty())
        }

    @Test
    fun `without a pending wait_for_user the next step is pushed as an app note`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returnsMany
                listOf(GuideStepResult(FIRST, showing = true), GuideStepResult(SECOND, showing = true))
            val calls = calls()
            val outcome = show(calls)
            nextOutcome()

            outcome.complete(GuideOutcomeTexts.SCREEN_CHANGED)
            runCurrent()

            assertTrue(notes.single().contains(SECOND))
            assertEquals(1, answers.size)
            coVerify(exactly = 2) { guide.showNextStep(GOAL, listener) }
        }

    @Test
    fun `a timed-out step pushes a reminder and keeps watching`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returns GuideStepResult(FIRST, showing = true)
            val calls = calls()
            val outcome = show(calls)
            val again = nextOutcome()

            outcome.complete(GuideOutcomeTexts.TIMED_OUT)
            runCurrent()

            assertTrue(notes.single().startsWith("앱 알림"))
            assertTrue(again.isActive && outcomes.isEmpty())
            coVerify(exactly = 1) { guide.showNextStep(GOAL, listener) }
        }

    @Test
    fun `wait_for_user with nothing shown answers at once`() =
        runTest {
            val calls = calls()

            calls.start(call("w1", RealtimeProtocol.WAIT_FOR_USER))
            runCurrent()

            assertEquals("w1" to GuideOutcomeTexts.NOTHING_HIGHLIGHTED, answers.single())
            coVerify(exactly = 0) { guide.waitForUser() }
        }

    @Test
    fun `a reached goal is announced and not planned again`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returnsMany
                listOf(
                    GuideStepResult(FIRST, showing = true),
                    GuideStepResult(DONE_SAY, showing = false, kind = "done"),
                )
            val calls = calls()
            val outcome = show(calls)

            outcome.complete(GuideOutcomeTexts.USER_DID_IT)
            runCurrent()

            assertTrue(notes.single().contains(DONE_SAY))
            calls.start(call("w2", RealtimeProtocol.WAIT_FOR_USER))
            runCurrent()
            assertEquals(GuideOutcomeTexts.NOTHING_HIGHLIGHTED, answers.last().second)
            coVerify(exactly = 2) { guide.showNextStep(GOAL, listener) }
        }

    @Test
    fun `a closed guidance asks the user what they want`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returns GuideStepResult(FIRST, showing = true)
            val calls = calls()
            val outcome = show(calls)

            outcome.complete(GuideOutcomeTexts.CLOSED)
            runCurrent()

            assertTrue(notes.single().contains("닫았어요"))
            coVerify(exactly = 1) { guide.showNextStep(GOAL, listener) }
        }

    @Test
    fun `a step replaced by a newer one is ignored`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returns GuideStepResult(FIRST, showing = true)
            val calls = calls()
            val first = show(calls)
            show(calls)

            first.complete(GuideOutcomeTexts.USER_DID_IT)
            runCurrent()

            assertTrue(notes.isEmpty())
            coVerify(exactly = 2) { guide.showNextStep(GOAL, listener) }
        }

    private companion object {
        const val GOAL = "설정 열기"
        const val FIRST = "화면 오른쪽 위에서 아래로 쓸어내려 주세요"
        const val SECOND = "설정 톱니바퀴를 눌러 주세요"
        const val DONE_SAY = "설정이 열렸어요"
    }
}
