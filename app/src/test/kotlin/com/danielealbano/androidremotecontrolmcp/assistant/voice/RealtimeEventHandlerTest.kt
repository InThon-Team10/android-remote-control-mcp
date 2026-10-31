package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RecordingAgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideStepResult
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("RealtimeEventHandler")
class RealtimeEventHandlerTest {
    private val sent = mutableListOf<JsonObject>()
    private val log = mutableListOf<String>()
    private var pending = 0L
    private val playback = mockk<AudioPlayback>(relaxed = true)
    private val guide = mockk<GuideSteps>(relaxed = true)

    init {
        every { playback.pendingMs() } answers { pending }
    }

    private val events =
        object : LiveEvents {
            override fun userText(text: String) {
                log += "user:$text"
            }

            override fun assistantText(text: String) {
                log += "assistant:$text"
            }

            override fun turnEnd() {
                log += "turnEnd"
            }

            override fun speaking() {
                log += "speaking"
            }

            override fun listening() {
                log += "listening"
            }

            override fun end(outcome: AgentOutcome) {
                log += "end:$outcome"
            }
        }

    private val listener = RecordingAgentListener()

    private fun TestScope.handler(): RealtimeEventHandler {
        val handler = RealtimeEventHandler(sent::add, playback, guide, listener, backgroundScope, events)
        return handler
    }

    private fun types() = sent.map { it["type"]!!.jsonPrimitive.content }

    private fun JsonObject.item() = this["item"]!!.jsonObject

    @Test
    fun `audio is played, speaking is reported and the reply ends in listening`() =
        runTest {
            val handler = handler()

            handler.handle(RealtimeTestEvents.created("r1"))
            handler.handle(RealtimeTestEvents.audio("r1", "i1", 960))
            handler.handle(RealtimeTestEvents.done())

            verify { playback.write(match { it.size == 960 }) }
            assertEquals(listOf("speaking", "turnEnd", "listening"), log)
        }

    @Test
    fun `transcripts reach the owner`() =
        runTest {
            val handler = handler()

            handler.handle(RealtimeTestEvents.transcript("r1", "안녕"))
            handler.handle(RealtimeTestEvents.userSaid("도와주세요"))
            handler.handle(RealtimeTestEvents.userSaid("  "))

            assertEquals(listOf("assistant:안녕", "user:도와주세요"), log)
        }

    @Test
    fun `barge in flushes the speaker, truncates at the played ms and cuts the cancelled reply`() =
        runTest {
            val handler = handler()
            handler.handle(RealtimeTestEvents.created("r1"))
            handler.handle(RealtimeTestEvents.audio("r1", "i1", 48 * 1000))
            pending = 400

            handler.handle(RealtimeTestEvents.type("input_audio_buffer.speech_started"))
            log.clear()
            handler.handle(RealtimeTestEvents.audio("r1", "i1", 960))
            handler.handle(RealtimeTestEvents.transcript("r1", "late"))

            verify(exactly = 1) { playback.flush() }
            verify(exactly = 1) { playback.write(any()) }
            val truncate = sent.single()
            assertEquals("conversation.item.truncate", truncate["type"]!!.jsonPrimitive.content)
            assertEquals("i1", truncate["item_id"]!!.jsonPrimitive.content)
            assertEquals(0, truncate["content_index"]!!.jsonPrimitive.int)
            assertEquals(600, truncate["audio_end_ms"]!!.jsonPrimitive.int)
            assertTrue(log.isEmpty())
        }

    @Test
    fun `barge in with nothing left to play sends no truncate`() =
        runTest {
            val handler = handler()
            handler.handle(RealtimeTestEvents.audio("r1", "i1", 960))
            pending = 0

            handler.handle(RealtimeTestEvents.type("input_audio_buffer.speech_started"))

            assertTrue(sent.isEmpty())
        }

    @Test
    fun `a reply after the barge in is played again`() =
        runTest {
            val handler = handler()
            handler.handle(RealtimeTestEvents.created("r1"))
            handler.handle(RealtimeTestEvents.type("input_audio_buffer.speech_started"))
            handler.handle(RealtimeTestEvents.done("cancelled"))
            handler.handle(RealtimeTestEvents.created("r2"))
            handler.handle(RealtimeTestEvents.audio("r2", "i2", 960))

            verify(exactly = 1) { playback.write(any()) }
        }

    @Test
    fun `show_next_step runs the guide and answers with the result then asks for a reply`() =
        runTest {
            coEvery { guide.showNextStep("카톡 열기", any()) } returns GuideStepResult("앱을 눌러 주세요", true)
            val handler = handler()

            handler.handle(RealtimeTestEvents.call("show_next_step", "c1", """{"goal":"카톡 열기"}"""))
            runCurrent()

            assertEquals(listOf("conversation.item.create", "response.create"), types())
            assertEquals("function_call_output", sent[0].item()["type"]!!.jsonPrimitive.content)
            assertEquals("c1", sent[0].item()["call_id"]!!.jsonPrimitive.content)
            val output = sent[0].item()["output"]!!.jsonPrimitive.content
            assertTrue(output.contains("앱을 눌러 주세요") && output.contains("\"showing\":true"))
        }

    @Test
    fun `show_next_step without a goal is answered without calling the guide`() =
        runTest {
            val handler = handler()

            handler.handle(RealtimeTestEvents.call("show_next_step", "c1", "not json"))
            runCurrent()

            coVerify(exactly = 0) { guide.showNextStep(any(), any()) }
            assertEquals(listOf("conversation.item.create", "response.create"), types())
        }

    @Test
    fun `a pending wait_for_user lets the conversation continue and answers when it resolves`() =
        runTest {
            val waiting = CompletableDeferred<String>()
            coEvery { guide.waitForUser() } coAnswers { waiting.await() }
            val handler = handler()
            handler.handle(RealtimeTestEvents.call("wait_for_user", "w1"))
            runCurrent()

            handler.handle(RealtimeTestEvents.userSaid("이게 뭐예요?"))
            handler.handle(RealtimeTestEvents.audio("r2", "i2", 960))
            runCurrent()
            assertTrue(sent.isEmpty())
            assertEquals(listOf("user:이게 뭐예요?", "speaking"), log)

            waiting.complete("The user did it.")
            runCurrent()

            assertEquals(listOf("conversation.item.create", "response.create"), types())
            assertEquals("The user did it.", sent[0].item()["output"]!!.jsonPrimitive.content)
        }

    @Test
    fun `response create is queued while a response is active and sent when it is done`() =
        runTest {
            coEvery { guide.waitForUser() } returns "Timed out."
            val handler = handler()
            handler.handle(RealtimeTestEvents.created("r1"))

            handler.handle(RealtimeTestEvents.call("wait_for_user", "w1"))
            runCurrent()
            assertEquals(listOf("conversation.item.create"), types())

            handler.handle(RealtimeTestEvents.done())

            assertEquals(listOf("conversation.item.create", "response.create"), types())
        }

    @Test
    fun `two outputs queued behind one response ask for a single reply`() =
        runTest {
            coEvery { guide.waitForUser() } returns "ok"
            val handler = handler()
            handler.handle(RealtimeTestEvents.created("r1"))
            handler.handle(RealtimeTestEvents.call("wait_for_user", "w1"))
            handler.handle(RealtimeTestEvents.call("wait_for_user", "w2"))
            runCurrent()

            handler.handle(RealtimeTestEvents.done())

            assertEquals(1, types().count { it == "response.create" })
            assertEquals(2, types().count { it == "conversation.item.create" })
        }

    @Test
    fun `typed text becomes a user message followed by a response request`() =
        runTest {
            val handler = handler()

            handler.sendText("와이파이 켜 줘")

            assertEquals(listOf("user:와이파이 켜 줘", "turnEnd"), log)
            assertEquals(listOf("conversation.item.create", "response.create"), types())
            val content = sent[0].item()["content"]!!.toString()
            assertTrue(content.contains("input_text") && content.contains("와이파이 켜 줘"))
        }

    @Test
    fun `an unknown tool is answered with a text`() =
        runTest {
            val handler = handler()

            handler.handle(RealtimeTestEvents.call("other", "c1"))
            runCurrent()

            assertTrue(
                sent[0]
                    .item()["output"]!!
                    .jsonPrimitive.content
                    .contains("Unknown tool"),
            )
        }

    @Test
    fun `a guide failure ends the conversation with the typed error`() =
        runTest {
            coEvery { guide.showNextStep(any(), any()) } throws
                com.danielealbano.androidremotecontrolmcp.assistant
                    .AssistantException(AssistantError.ModelAuthFailed)
            val handler = handler()

            handler.handle(RealtimeTestEvents.call("show_next_step", "c1", """{"goal":"x"}"""))
            runCurrent()

            assertEquals(listOf("end:${AgentOutcome.Failed(AssistantError.ModelAuthFailed)}"), log)
            assertTrue(sent.isEmpty())
        }

    @Test
    fun `server errors map to outcomes and benign ones are ignored`() =
        runTest {
            val handler = handler()

            handler.handle(RealtimeTestEvents.error("invalid_value"))
            handler.handle(RealtimeTestEvents.error("invalid_api_key"))
            handler.handle(RealtimeTestEvents.error("insufficient_quota"))
            handler.handle(RealtimeTestEvents.error(null, type = "server_error"))
            handler.handle(RealtimeTestEvents.error("session_expired"))

            assertEquals(
                listOf(
                    AgentOutcome.Failed(AssistantError.ModelAuthFailed),
                    AgentOutcome.Failed(AssistantError.ModelQuotaExceeded),
                    AgentOutcome.Failed(AssistantError.ModelUnavailable),
                    AgentOutcome.Completed,
                ).map { "end:$it" },
                log,
            )
        }

    @Test
    fun `a failed response ends the conversation`() =
        runTest {
            val handler = handler()

            handler.handle(RealtimeTestEvents.done("failed", "rate_limit_exceeded"))

            assertEquals(
                "end:${AgentOutcome.Failed(AssistantError.ModelQuotaExceeded)}",
                log.last(),
            )
        }
}
