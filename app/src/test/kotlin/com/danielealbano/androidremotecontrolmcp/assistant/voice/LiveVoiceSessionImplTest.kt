package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AssistantAgent
import com.danielealbano.androidremotecontrolmcp.assistant.agent.ElderGuidancePrompt
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiToolCallRunner
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.NoticeKind
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import com.danielealbano.androidremotecontrolmcp.assistant.session.ToolCallStatus
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.google.genai.types.Blob
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.LiveConnectConfig
import com.google.genai.types.LiveServerContent
import com.google.genai.types.LiveServerGoAway
import com.google.genai.types.LiveServerMessage
import com.google.genai.types.LiveServerToolCall
import com.google.genai.types.LiveServerToolCallCancellation
import com.google.genai.types.Part
import com.google.genai.types.Transcription
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("LiveVoiceSessionImpl")
class LiveVoiceSessionImplTest {
    private val configFlow = MutableStateFlow(READY)
    private val settings =
        mockk<AssistantSettings> {
            every { config } returns configFlow
            coEvery { getConfig() } answers { configFlow.value }
            coEvery { getApiKey() } returns "key"
        }
    private val settingsRepository =
        mockk<SettingsRepository> { coEvery { getServerConfig() } returns ServerConfig() }
    private val connection =
        mockk<AssistantMcpConnection> {
            coEvery { listTools() } returns Result.success(listOf(tool("android_tap"), tool("android_close_app")))
            coEvery { callTool(any(), any()) } returns Result.success(McpToolUtils.textResult("ok"))
        }
    private val mic = Channel<ByteArray>(Channel.UNLIMITED)
    private val capture =
        mockk<AudioCapture> {
            every { hasPermission() } returns true
            every { chunks() } returns mic.receiveAsFlow()
        }
    private val playback = mockk<AudioPlayback>(relaxed = true)
    private val launcher = mockk<VoiceServiceLauncher>(relaxed = true)
    private val link = mockk<LiveConnection>(relaxed = true)
    private val connectConfig = slot<LiveConnectConfig>()
    private lateinit var onMessage: (LiveServerMessage) -> Unit
    private lateinit var onClosed: (Throwable?) -> Unit
    private val gateway =
        object : LiveGateway {
            var connects = 0

            override suspend fun connect(
                apiKey: String,
                config: LiveConnectConfig,
                onMessage: (LiveServerMessage) -> Unit,
                onClosed: (Throwable?) -> Unit,
            ): LiveConnection {
                connects++
                connectConfig.captured = config
                this@LiveVoiceSessionImplTest.onMessage = onMessage
                this@LiveVoiceSessionImplTest.onClosed = onClosed
                return link
            }
        }

    private class Fixture(
        val live: LiveVoiceSessionImpl,
        val session: AssistantSession,
    )

    private fun TestScope.fixture(): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AssistantSession(mockk<AssistantAgent>(relaxed = true), settings, dispatcher)
        val deps =
            LiveVoiceDependencies(
                settings,
                settingsRepository,
                session,
                connection,
                GeminiToolCallRunner(connection),
                dispatcher,
            )
        return Fixture(LiveVoiceSessionImpl(gateway, capture, playback, launcher, deps), session).also { runCurrent() }
    }

    private suspend fun Fixture.begin() {
        assertTrue(live.start().isSuccess)
    }

    private fun content(build: LiveServerContent.Builder.() -> Unit): LiveServerMessage =
        LiveServerMessage.builder().serverContent(LiveServerContent.builder().apply(build).build()).build()

    private fun transcription(text: String) = Transcription.builder().text(text).build()

    private fun toolMessage(name: String): LiveServerMessage =
        LiveServerMessage
            .builder()
            .toolCall(
                LiveServerToolCall
                    .builder()
                    .functionCalls(
                        listOf(
                            FunctionCall
                                .builder()
                                .name(name)
                                .id("id-$name")
                                .build(),
                        ),
                    ).build(),
            ).build()

    @Test
    fun `gates fail start with a typed error and never connect`() =
        runTest {
            val cases =
                listOf(
                    READY.copy(enabled = false) to AssistantError.AssistantDisabled,
                    READY.copy(privacyNoticeAccepted = false) to AssistantError.PrivacyNoticeNotAccepted,
                    READY.copy(voiceEnabled = false) to AssistantError.VoiceDisabled,
                )
            for ((config, expected) in cases) {
                configFlow.value = config
                val fx = fixture()

                val error = (fx.live.start().exceptionOrNull() as AssistantException).error

                assertEquals(expected, error)
                assertEquals(LiveVoiceState.Error(expected), fx.live.state.value)
            }
            configFlow.value = READY
            every { capture.hasPermission() } returns false
            assertEquals(
                AssistantError.MicrophonePermissionMissing,
                (fixture().live.start().exceptionOrNull() as AssistantException).error,
            )
            every { capture.hasPermission() } returns true
            coEvery { settings.getApiKey() } returns null
            assertEquals(
                AssistantError.ApiKeyMissing,
                (fixture().live.start().exceptionOrNull() as AssistantException).error,
            )
            assertEquals(0, gateway.connects)
        }

    @Test
    fun `start connects with tools and audio config, stop releases everything`() =
        runTest {
            val fx = fixture()

            fx.begin()
            fx.live.start()
            runCurrent()

            assertEquals(1, gateway.connects)
            assertEquals(LiveVoiceState.Listening, fx.live.state.value)
            assertEquals(SessionState.Running, fx.session.state.value)
            assertEquals(
                1,
                connectConfig.captured
                    .tools()
                    .get()
                    .size,
            )
            verify { playback.start() }
            verify { launcher.start() }

            fx.live.stop()
            runCurrent()

            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
            assertEquals(SessionState.Idle, fx.session.state.value)
            verify { link.close() }
            verify { playback.release() }
            verify { launcher.stop() }
        }

    @Test
    fun `microphone chunks are forwarded to the live connection`() =
        runTest {
            val fx = fixture()
            fx.begin()

            mic.trySend(byteArrayOf(1, 2, 3))
            runCurrent()

            coVerify { link.sendAudio(match { it.contentEquals(byteArrayOf(1, 2, 3)) }) }
            fx.live.stop()
        }

    @Test
    fun `transcripts coalesce into one item per side until the turn completes`() =
        runTest {
            val fx = fixture()
            fx.begin()

            onMessage(content { inputTranscription(transcription("Hel")) })
            onMessage(content { inputTranscription(transcription("lo")) })
            onMessage(content { outputTranscription(transcription("Hi")) })
            onMessage(content { outputTranscription(transcription(" you")) })
            onMessage(content { turnComplete(true) })
            onMessage(content { outputTranscription(transcription("More")) })
            runCurrent()

            assertEquals(
                listOf<ChatItem>(
                    ChatItem.User(1, "Hello"),
                    ChatItem.Assistant(2, "Hi you"),
                    ChatItem.Assistant(3, "More"),
                ),
                fx.session.items.value,
            )
            fx.live.stop()
        }

    @Test
    fun `model audio is played, interruption flushes it and turn complete returns to listening`() =
        runTest {
            val fx = fixture()
            fx.begin()
            val audio =
                Blob
                    .builder()
                    .mimeType("audio/pcm;rate=24000")
                    .data(byteArrayOf(9))
                    .build()
            val turn = Content.builder().parts(listOf(Part.builder().inlineData(audio).build())).build()

            onMessage(content { modelTurn(turn) })
            runCurrent()
            assertEquals(LiveVoiceState.Speaking, fx.live.state.value)
            verify { playback.write(match { it.contentEquals(byteArrayOf(9)) }) }

            onMessage(content { interrupted(true) })
            runCurrent()
            verify { playback.flush() }

            onMessage(content { turnComplete(true) })
            runCurrent()
            assertEquals(LiveVoiceState.Listening, fx.live.state.value)
            fx.live.stop()
        }

    @Test
    fun `allowed tool call runs the tool and sends the tool response`() =
        runTest {
            val fx = fixture()
            fx.begin()
            val sent = slot<List<FunctionResponse>>()
            coEvery { link.sendToolResponse(capture(sent)) } returns Unit

            onMessage(toolMessage("android_tap"))
            runCurrent()

            assertEquals(
                "android_tap",
                sent.captured
                    .single()
                    .name()
                    .get(),
            )
            assertEquals(
                "id-android_tap",
                sent.captured
                    .single()
                    .id()
                    .get(),
            )
            val call =
                fx.session.items.value
                    .filterIsInstance<ChatItem.ToolCall>()
                    .single()
            assertEquals(ToolCallStatus.DONE, call.status)
            fx.live.stop()
        }

    @Test
    fun `refused tools are not declared and a call to one is answered with an error`() =
        runTest {
            val fx = fixture()
            fx.begin()
            val sent = slot<List<FunctionResponse>>()
            coEvery { link.sendToolResponse(capture(sent)) } returns Unit

            onMessage(toolMessage("android_close_app"))
            runCurrent()

            val declared =
                connectConfig.captured
                    .tools()
                    .get()
                    .single()
                    .functionDeclarations()
                    .get()
                    .map { it.name().get() }
            assertEquals(listOf("android_tap"), declared)
            assertEquals(SessionState.Running, fx.session.state.value)
            assertTrue(
                sent.captured
                    .single()
                    .response()
                    .get()
                    .containsKey("error"),
            )
            coVerify(exactly = 0) { connection.callTool("android_close_app", any()) }
            fx.live.stop()
        }

    @Test
    fun `live session uses the elder guidance prompt with the voice rules`() =
        runTest {
            fixture().begin()

            assertTrue(ElderGuidancePrompt.VOICE.startsWith(ElderGuidancePrompt.TEXT))
            assertEquals(
                ElderGuidancePrompt.VOICE,
                connectConfig.captured
                    .systemInstruction()
                    .get()
                    .parts()
                    .get()
                    .single()
                    .text()
                    .get(),
            )
        }

    @Test
    fun `tool call cancellation cancels the matching in-flight call and sends no response`() =
        runTest {
            val fx = fixture()
            fx.begin()
            var cancelled = false
            coEvery { connection.callTool("android_tap", any()) } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }

            onMessage(toolMessage("android_tap"))
            runCurrent()
            onMessage(
                LiveServerMessage
                    .builder()
                    .toolCallCancellation(
                        LiveServerToolCallCancellation.builder().ids(listOf("id-android_tap")).build(),
                    ).build(),
            )
            runCurrent()

            assertTrue(cancelled)
            coVerify(exactly = 0) { link.sendToolResponse(any()) }
            assertEquals(LiveVoiceState.Listening, fx.live.state.value)
            fx.live.stop()
        }

    @Test
    fun `step limit ends the conversation with a notice`() =
        runTest {
            configFlow.value = READY.copy(stepLimit = 1)
            val fx = fixture()
            fx.begin()

            onMessage(toolMessage("android_tap"))
            runCurrent()
            onMessage(toolMessage("android_tap"))
            runCurrent()

            assertEquals(
                NoticeKind.StepLimitReached(1),
                (
                    fx.session.items.value
                        .last() as ChatItem.Notice
                ).kind,
            )
            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
            verify { link.close() }
        }

    @Test
    fun `go away ends the conversation with an error and releases resources`() =
        runTest {
            val fx = fixture()
            fx.begin()

            onMessage(LiveServerMessage.builder().goAway(LiveServerGoAway.builder().build()).build())
            runCurrent()

            assertEquals(LiveVoiceState.Error(AssistantError.GeminiUnavailable), fx.live.state.value)
            assertEquals(
                AssistantError.GeminiUnavailable,
                (
                    fx.session.items.value
                        .last() as ChatItem.Error
                ).error,
            )
            verify { playback.release() }
            verify { launcher.stop() }
            assertEquals(SessionState.Idle, fx.session.state.value)
        }

    @Test
    fun `session stop and disabling the assistant end the live conversation`() =
        runTest {
            val fx = fixture()
            fx.begin()

            fx.session.stop()
            runCurrent()
            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
            verify { launcher.stop() }

            fx.begin()
            configFlow.value = READY.copy(enabled = false)
            runCurrent()
            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
        }

    private fun tool(name: String) = Tool(name = name, description = "d", inputSchema = ToolSchema())

    private companion object {
        val READY =
            AssistantConfig(
                enabled = true,
                voiceEnabled = true,
                privacyNoticeAccepted = true,
                apiKeyConfigured = true,
            )
    }
}
