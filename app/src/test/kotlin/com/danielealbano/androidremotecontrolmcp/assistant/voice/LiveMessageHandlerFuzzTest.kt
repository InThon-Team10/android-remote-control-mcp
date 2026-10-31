package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiToolCallRunner
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RecordingAgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RunContext
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import com.google.genai.types.Blob
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.LiveServerContent
import com.google.genai.types.LiveServerMessage
import com.google.genai.types.LiveServerToolCall
import com.google.genai.types.Part
import com.google.genai.types.Transcription
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("LiveMessageHandler fuzz")
class LiveMessageHandlerFuzzTest {
    private class Events : LiveEvents {
        val log = CopyOnWriteArrayList<String>()
        val ends = CopyOnWriteArrayList<AgentOutcome>()

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
            ends += outcome
        }
    }

    private class Link : LiveConnection {
        val responses = CopyOnWriteArrayList<List<FunctionResponse>>()

        override suspend fun sendAudio(pcm: ByteArray) = Unit

        override suspend fun sendToolResponse(responses: List<FunctionResponse>) {
            this.responses += responses.toList()
        }

        override fun close() = Unit
    }

    private class Playback : AudioPlayback {
        val log = CopyOnWriteArrayList<String>()

        override fun start() = Unit

        override fun write(pcm: ByteArray) {
            log += "write"
        }

        override fun flush() {
            log += "flush"
        }

        override fun release() = Unit
    }

    private class Connection : AssistantMcpConnection {
        val executed = AtomicInteger()

        override suspend fun connect(): Result<Unit> = Result.success(Unit)

        override suspend fun listTools(): Result<List<Tool>> = Result.success(emptyList())

        override suspend fun callTool(
            name: String,
            arguments: JsonObject,
        ): Result<CallToolResult> {
            executed.incrementAndGet()
            return Result.success(CallToolResult(content = listOf(TextContent("ok"))))
        }

        override suspend fun close() = Unit
    }

    private fun context(stepLimit: Int) =
        RunContext("key", "model", GenerateContentConfig.builder().build(), stepLimit, "", setOf("android_tap"))

    private fun toolMessage(calls: Int): LiveServerMessage =
        LiveServerMessage
            .builder()
            .toolCall(
                LiveServerToolCall
                    .builder()
                    .functionCalls(
                        List(calls) {
                            FunctionCall
                                .builder()
                                .id("c$it")
                                .name("android_tap")
                                .args(mapOf("a" to it))
                                .build()
                        },
                    ).build(),
            ).build()

    private fun contentMessage(build: LiveServerContent.Builder.() -> Unit): LiveServerMessage =
        LiveServerMessage.builder().serverContent(LiveServerContent.builder().apply(build).build()).build()

    private fun transcription(text: String) = Transcription.builder().text(text).build()

    /** Feeds random messages to a handler and records what the contract says must come out. */
    private inner class Scenario(
        private val random: Random,
        val stepLimit: Int,
        private val testScope: TestScope,
    ) {
        val events = Events()
        val link = Link()
        val playback = Playback()
        val connection = Connection()
        val expectedEvents = mutableListOf<String>()
        val expectedPlayback = mutableListOf<String>()
        var batches = 0
        var callsInAcceptedBatches = 0
        private val handler =
            LiveMessageHandler(
                link,
                playback,
                LiveToolRunner(GeminiToolCallRunner(connection), RecordingAgentListener(), context(stepLimit)),
                testScope,
                events,
            )

        fun step() {
            when (random.nextInt(6)) {
                0 -> transcript(user = true)
                1 -> transcript(user = false)
                2 -> audio()
                3 -> turnSignals()
                else -> toolBatch()
            }
        }

        private fun transcript(user: Boolean) {
            val text = AssistantFuzzGen.string(random)
            if (user) {
                handler.handle(contentMessage { inputTranscription(transcription(text)) })
            } else {
                handler.handle(contentMessage { outputTranscription(transcription(text)) })
            }
            expectedEvents += (if (user) "user:" else "assistant:") + text
        }

        private fun audio() {
            val chunks = random.nextInt(3)
            val blob =
                Blob
                    .builder()
                    .data(ByteArray(2))
                    .mimeType("audio/pcm")
                    .build()
            val parts = List(chunks) { Part.builder().inlineData(blob).build() }
            handler.handle(
                contentMessage {
                    modelTurn(
                        Content
                            .builder()
                            .role("model")
                            .parts(parts)
                            .build(),
                    )
                },
            )
            repeat(chunks) {
                expectedEvents += "speaking"
                expectedPlayback += "write"
            }
        }

        private fun turnSignals() {
            val interrupted = random.nextBoolean()
            val complete = random.nextBoolean()
            handler.handle(
                contentMessage {
                    if (interrupted) interrupted(true)
                    if (complete) turnComplete(true)
                },
            )
            if (interrupted) {
                expectedEvents += "turnEnd"
                expectedPlayback += "flush"
            }
            if (complete) expectedEvents += listOf("turnEnd", "listening")
        }

        private fun toolBatch() {
            val calls = 1 + random.nextInt(3)
            handler.handle(toolMessage(calls))
            batches++
            if (batches <= stepLimit) callsInAcceptedBatches += calls
        }

        fun drain() = testScope.runCurrent()
    }

    @Test
    fun `random message sequences keep event order and count tool batches against the step limit`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            val scenario = Scenario(random, 1 + random.nextInt(6), TestScope(StandardTestDispatcher()))
            repeat(120) { scenario.step() }
            scenario.drain()
            val label = "seed=$seed stepLimit=${scenario.stepLimit} batches=${scenario.batches}"
            assertEquals(scenario.expectedEvents, scenario.events.log.toList(), label)
            assertEquals(scenario.expectedPlayback, scenario.playback.log.toList(), label)
            assertEquals(scenario.callsInAcceptedBatches, scenario.connection.executed.get(), label)
            assertEquals(scenario.callsInAcceptedBatches, scenario.link.responses.sumOf { it.size }, label)
            val limitEnds = scenario.events.ends.filterIsInstance<AgentOutcome.StepLimitReached>()
            assertEquals(maxOf(0, scenario.batches - scenario.stepLimit), limitEnds.size, label)
            assertTrue(limitEnds.all { it.limit == scenario.stepLimit }, label)
        }
    }

    @Test
    fun `concurrent tool-call batches never exceed the step limit`() {
        val stepLimit = 5
        var exceeded = 0
        repeat(300) { round ->
            val link = Link()
            val connection = Connection()
            val events = Events()
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.Default)
            val handler =
                LiveMessageHandler(
                    link,
                    Playback(),
                    LiveToolRunner(GeminiToolCallRunner(connection), RecordingAgentListener(), context(stepLimit)),
                    scope,
                    events,
                )
            repeat(200) { handler.handle(toolMessage(1)) }
            runBlocking { job.children.toList().forEach { it.join() } }
            if (connection.executed.get() > stepLimit) exceeded++
            assertTrue(
                connection.executed.get() <= stepLimit,
                "round=$round executed ${connection.executed.get()} batches, limit $stepLimit (rounds over: $exceeded)",
            )
        }
    }
}
