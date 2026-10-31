package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideOutcomeTexts
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideStepResult
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    private val outputs = mutableListOf<String>()
    private val failures = mutableListOf<Throwable>()

    private fun TestScope.calls(): RealtimeFunctionCalls {
        val respond: (String, String) -> Unit = { _, output -> outputs += output }
        return RealtimeFunctionCalls(guide, listener, this, respond) { failures += it }
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

    private fun TestScope.showThenWait(calls: RealtimeFunctionCalls) {
        calls.start(call("c1", RealtimeProtocol.SHOW_NEXT_STEP, """{"goal":"$GOAL"}"""))
        advanceUntilIdle()
        calls.start(call("c2", RealtimeProtocol.WAIT_FOR_USER))
        advanceUntilIdle()
    }

    @Test
    fun `a finished step comes back with the next step already shown`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returnsMany
                listOf(GuideStepResult(FIRST, showing = true), GuideStepResult(SECOND, showing = true))
            coEvery { guide.waitForUser() } returns GuideOutcomeTexts.USER_DID_IT

            showThenWait(calls())

            val answer = Json.parseToJsonElement(outputs.last()).jsonObject
            assertEquals(GuideOutcomeTexts.USER_DID_IT, answer["result"]!!.jsonPrimitive.content)
            assertEquals(SECOND, answer["next"]!!.jsonObject["say"]!!.jsonPrimitive.content)
            coVerify(exactly = 2) { guide.showNextStep(GOAL, listener) }
            assertTrue(failures.isEmpty())
        }

    @Test
    fun `a screen change also plans the next step`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returnsMany
                listOf(GuideStepResult(FIRST, showing = true), GuideStepResult(SECOND, showing = true))
            coEvery { guide.waitForUser() } returns GuideOutcomeTexts.SCREEN_CHANGED

            showThenWait(calls())

            val answer = Json.parseToJsonElement(outputs.last()).jsonObject
            assertEquals(SECOND, answer["next"]!!.jsonObject["say"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a timed-out step is reported without planning again`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returns GuideStepResult(FIRST, showing = true)
            coEvery { guide.waitForUser() } returns GuideOutcomeTexts.TIMED_OUT

            showThenWait(calls())

            assertEquals(GuideOutcomeTexts.TIMED_OUT, outputs.last())
            coVerify(exactly = 1) { guide.showNextStep(GOAL, listener) }
        }

    @Test
    fun `without a goal the outcome is reported as is`() =
        runTest {
            coEvery { guide.waitForUser() } returns GuideOutcomeTexts.USER_DID_IT
            val calls = calls()

            calls.start(call("c1", RealtimeProtocol.WAIT_FOR_USER))
            advanceUntilIdle()

            assertEquals(GuideOutcomeTexts.USER_DID_IT, outputs.single())
            coVerify(exactly = 0) { guide.showNextStep(any(), any()) }
        }

    @Test
    fun `a goal reached ends it so a later wait does not plan again`() =
        runTest {
            coEvery { guide.showNextStep(GOAL, listener) } returnsMany
                listOf(
                    GuideStepResult(FIRST, showing = true),
                    GuideStepResult(DONE_SAY, showing = false, kind = "done"),
                )
            coEvery { guide.waitForUser() } returns GuideOutcomeTexts.USER_DID_IT
            val calls = calls()
            showThenWait(calls)

            calls.start(call("c3", RealtimeProtocol.WAIT_FOR_USER))
            advanceUntilIdle()

            assertEquals(GuideOutcomeTexts.USER_DID_IT, outputs.last())
            coVerify(exactly = 2) { guide.showNextStep(GOAL, listener) }
        }

    private companion object {
        const val GOAL = "설정 열기"
        const val FIRST = "화면 오른쪽 위에서 아래로 쓸어내려 주세요"
        const val SECOND = "설정 톱니바퀴를 눌러 주세요"
        const val DONE_SAY = "설정이 열렸어요"
    }
}
