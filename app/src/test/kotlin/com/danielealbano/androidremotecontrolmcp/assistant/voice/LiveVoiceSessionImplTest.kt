package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AssistantAgent
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings as Settings

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("LiveVoiceSessionImpl")
class LiveVoiceSessionImplTest {
    private val configFlow = MutableStateFlow(READY)
    private val settings =
        mockk<Settings> {
            every { config } returns configFlow
            coEvery { getConfig() } answers { configFlow.value }
            coEvery { getApiKey() } returns "key"
        }
    private val mic = Channel<ByteArray>(Channel.UNLIMITED)
    private val capture =
        mockk<AudioCapture> {
            every { hasPermission() } returns true
            every { chunks() } returns mic.receiveAsFlow()
        }
    private val playback = mockk<AudioPlayback>(relaxed = true)
    private val launcher = mockk<VoiceServiceLauncher>(relaxed = true)
    private val guide = mockk<GuideSteps>(relaxed = true)
    private val sent = mutableListOf<JsonObject>()
    private var closed = false
    private var acks = true
    private var sendBlock: suspend () -> Unit = {}
    private lateinit var onEvent: (JsonObject) -> Unit
    private lateinit var onClosed: (Throwable?) -> Unit
    private val gateway =
        object : RealtimeGateway {
            var connects = 0

            override suspend fun connect(
                apiKey: String,
                onEvent: (JsonObject) -> Unit,
                onClosed: (Throwable?) -> Unit,
            ): RealtimeLink {
                connects++
                this@LiveVoiceSessionImplTest.onEvent = onEvent
                this@LiveVoiceSessionImplTest.onClosed = onClosed
                return object : RealtimeLink {
                    override suspend fun send(event: JsonObject) {
                        sendBlock()
                        sent += event
                        if (acks && event["type"]?.jsonPrimitive?.content == "session.update") {
                            this@LiveVoiceSessionImplTest.onEvent(RealtimeTestEvents.type("session.updated"))
                        }
                    }

                    override fun close() {
                        closed = true
                    }
                }
            }
        }

    private class Fixture(
        val live: LiveVoiceSessionImpl,
        val session: AssistantSession,
    )

    private fun TestScope.fixture(): Fixture {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val session = AssistantSession(mockk<AssistantAgent>(relaxed = true), settings, dispatcher)
        val deps = LiveVoiceDependencies(settings, session, guide, dispatcher)
        return Fixture(LiveVoiceSessionImpl(gateway, capture, playback, launcher, deps), session).also { runCurrent() }
    }

    private suspend fun Fixture.begin() {
        assertTrue(live.start().isSuccess)
    }

    private fun types() = sent.map { it["type"]!!.jsonPrimitive.content }

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
    fun `start connects and configures the session, stop releases everything`() =
        runTest {
            val fx = fixture()

            fx.begin()
            fx.live.start()
            runCurrent()

            assertEquals(1, gateway.connects)
            assertEquals(listOf("session.update"), types())
            assertEquals(LiveVoiceState.Listening, fx.live.state.value)
            assertEquals(SessionState.Running, fx.session.state.value)
            verify { playback.start() }
            verify { launcher.start() }

            fx.live.stop()
            runCurrent()

            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
            assertEquals(SessionState.Idle, fx.session.state.value)
            assertTrue(closed)
            verify { playback.release() }
            verify { launcher.stop() }
            verify { guide.reset() }
        }

    @Test
    fun `microphone chunks are sent as audio appends after the session update`() =
        runTest {
            val fx = fixture()
            fx.begin()

            mic.trySend(byteArrayOf(1, 2, 3))
            runCurrent()

            assertEquals(listOf("session.update", "input_audio_buffer.append"), types())
            assertEquals("AQID", sent[1]["audio"]!!.jsonPrimitive.content)
            fx.live.stop()
        }

    @Test
    fun `events reach the handler and transcripts land in the session`() =
        runTest {
            val fx = fixture()
            fx.begin()

            onEvent(RealtimeTestEvents.userSaid("안녕"))
            onEvent(RealtimeTestEvents.transcript("r1", "네"))
            runCurrent()

            assertEquals(
                listOf<ChatItem>(ChatItem.User(1, "안녕"), ChatItem.Assistant(2, "네")),
                fx.session.items.value,
            )
            fx.live.stop()
        }

    @Test
    fun `audio events move the state to speaking and a finished reply to listening`() =
        runTest {
            val fx = fixture()
            fx.begin()

            onEvent(RealtimeTestEvents.audio("r1", "i1", 960))
            runCurrent()
            assertEquals(LiveVoiceState.Speaking, fx.live.state.value)

            onEvent(RealtimeTestEvents.done())
            runCurrent()
            assertEquals(LiveVoiceState.Listening, fx.live.state.value)
            fx.live.stop()
        }

    @Test
    fun `sendText sends the typed message and asks for a reply, and is ignored when stopped`() =
        runTest {
            val fx = fixture()
            fx.live.sendText("ignored")
            fx.begin()

            fx.live.sendText("와이파이")
            fx.live.sendText("  ")
            runCurrent()

            assertEquals(listOf("session.update", "conversation.item.create", "response.create"), types())
            fx.live.stop()
            runCurrent()
            fx.live.sendText("after stop")
            runCurrent()
            assertEquals(3, sent.size)
        }

    @Test
    fun `a link whose send is stuck ends the conversation as unavailable`() =
        runTest {
            val stuck = CompletableDeferred<Unit>()
            acks = false
            sendBlock = { stuck.await() }
            val fx = fixture()
            fx.begin()

            advanceTimeBy(15_001)
            runCurrent()

            assertEquals(LiveVoiceState.Error(AssistantError.ModelUnavailable), fx.live.state.value)
            assertEquals(SessionState.Idle, fx.session.state.value)
            verify { playback.release() }
        }

    @Test
    fun `a link that stops answering the heartbeat ends the conversation as unavailable`() =
        runTest {
            val fx = fixture()
            fx.begin()
            acks = false

            advanceTimeBy(80_000)
            runCurrent()

            assertEquals(LiveVoiceState.Error(AssistantError.ModelUnavailable), fx.live.state.value)
            assertTrue(types().count { it == "session.update" } >= 2)
        }

    @Test
    fun `the conversation ends gracefully before the 60 minute session limit`() =
        runTest {
            val fx = fixture()
            fx.begin()

            advanceTimeBy(58 * 60_000L)
            runCurrent()
            assertEquals(LiveVoiceState.Listening, fx.live.state.value)
            advanceTimeBy(2 * 60_000L)
            runCurrent()

            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
            verify { launcher.stop() }
        }

    @Test
    fun `a socket failure ends the conversation with an error and a clean close ends it quietly`() =
        runTest {
            val fx = fixture()
            fx.begin()

            onClosed(java.io.IOException("lost"))
            runCurrent()
            assertEquals(LiveVoiceState.Error(AssistantError.ModelUnavailable), fx.live.state.value)
            assertEquals(
                AssistantError.ModelUnavailable,
                (
                    fx.session.items.value
                        .last() as ChatItem.Error
                ).error,
            )

            fx.live.stop()
            fx.begin()
            onClosed(null)
            runCurrent()
            assertEquals(LiveVoiceState.Idle, fx.live.state.value)
        }

    @Test
    fun `a cancelled pending wait_for_user does not leak when the conversation stops`() =
        runTest {
            var cancelled = false
            coEvery { guide.waitForUser() } coAnswers {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            val fx = fixture()
            fx.begin()
            onEvent(RealtimeTestEvents.call("wait_for_user", "w1"))
            runCurrent()

            fx.live.stop()
            runCurrent()

            assertTrue(cancelled)
            assertEquals(listOf("session.update"), types())
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
