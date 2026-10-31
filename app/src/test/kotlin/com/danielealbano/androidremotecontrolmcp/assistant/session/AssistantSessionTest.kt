package com.danielealbano.androidremotecontrolmcp.assistant.session

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentInput
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

@DisplayName("AssistantSession")
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantSessionTest {
    private val agent = FakeAssistantAgent()
    private val configFlow = MutableStateFlow(READY)
    private val settings =
        mockk<AssistantSettings> {
            every { config } returns configFlow
            coEvery { getConfig() } answers { configFlow.value }
        }

    private fun TestScope.newSession(): AssistantSession =
        AssistantSession(agent, settings, StandardTestDispatcher(testScheduler)).also { runCurrent() }

    /** Configures a run that parks until the gate completes; returns its listener and gate. */
    private fun holdRun(): Pair<CompletableDeferred<AgentListener>, CompletableDeferred<Unit>> {
        val listener = CompletableDeferred<AgentListener>()
        val gate = CompletableDeferred<Unit>()
        agent.script = { _, l ->
            listener.complete(l)
            gate.await()
            AgentOutcome.Completed
        }
        return listener to gate
    }

    private fun TestScope.confirmAsync(listener: AgentListener): CompletableDeferred<Boolean> {
        val result = CompletableDeferred<Boolean>()
        backgroundScope.launch {
            runCatching { listener.confirm("c", "close_app") }
                .onSuccess { result.complete(it) }
                .onFailure { result.completeExceptionally(it) }
        }
        return result
    }

    private fun AssistantSession.last(): ChatItem = items.value.last()

    @Test
    fun `send appends user item runs agent and returns to idle`() =
        runTest {
            val session = newSession()

            session.send("hello")
            assertEquals(SessionState.Running, session.state.value)
            assertTrue(session.last() is ChatItem.User)
            runCurrent()

            assertEquals(AgentInput.Text("hello"), agent.inputs.single())
            assertEquals(SessionState.Idle, session.state.value)
        }

    @Test
    fun `send ignored when blank or while running`() =
        runTest {
            val session = newSession()
            val (_, gate) = holdRun()

            session.send("  ")
            session.send("one")
            runCurrent()
            session.send("two")
            runCurrent()

            assertEquals(1, agent.inputs.size)
            assertEquals(1, session.items.value.size)
            gate.complete(Unit)
        }

    @Test
    fun `beginLive marks the session running, refuses a second live and returns to idle when the job ends`() =
        runTest {
            val session = newSession()
            val job = Job()

            assertTrue(session.beginLive(job) != null)
            assertEquals(SessionState.Running, session.state.value)
            assertEquals(null, session.beginLive(Job()))
            job.cancel()

            assertEquals(SessionState.Idle, session.state.value)
        }

    @Test
    fun `stop and newChat cancel the live job`() =
        runTest {
            val session = newSession()
            val first = Job()
            session.beginLive(first)
            session.stop()
            val second = Job()
            session.beginLive(second)
            session.newChat()

            assertTrue(first.isCancelled)
            assertTrue(second.isCancelled)
        }

    @Test
    fun `live transcript chunks coalesce per side until the turn ends or another item is appended`() =
        runTest {
            val session = newSession()

            session.appendLiveUserText("Hel")
            session.appendLiveUserText("lo")
            session.appendLiveAssistantText("Hi")
            session.appendLiveAssistantText(" there")
            session.endLiveTurn()
            session.appendLiveAssistantText("Next")
            session.reportError(AssistantError.ModelUnavailable)
            session.appendLiveAssistantText("After")

            assertEquals(
                listOf<ChatItem>(
                    ChatItem.User(1, "Hello"),
                    ChatItem.Assistant(2, "Hi there"),
                    ChatItem.Assistant(3, "Next"),
                    ChatItem.Error(4, AssistantError.ModelUnavailable),
                    ChatItem.Assistant(5, "After"),
                ),
                session.items.value,
            )
        }

    @Test
    fun `gates refuse the run with a typed error and do not call the agent`() =
        runTest {
            val cases =
                listOf(
                    READY.copy(enabled = false) to AssistantError.AssistantDisabled,
                    READY.copy(privacyNoticeAccepted = false) to AssistantError.PrivacyNoticeNotAccepted,
                    READY.copy(apiKeyConfigured = false) to AssistantError.ApiKeyMissing,
                )
            for ((config, expected) in cases) {
                configFlow.value = config
                val session = newSession()
                session.send("x")
                runCurrent()

                assertEquals(expected, (session.last() as ChatItem.Error).error)
                assertEquals(SessionState.Idle, session.state.value)
            }
        }

    @Test
    fun `tool call lifecycle and error status`() =
        runTest {
            val session = newSession()
            val (listener, gate) = holdRun()
            session.send("x")
            runCurrent()
            val l = listener.await()

            l.onAssistantText("hi")
            l.onToolCallStarted("c1", "tap")
            l.onToolCallStarted("c2", "swipe")
            assertEquals(ToolCallStatus.RUNNING, (session.items.value[2] as ChatItem.ToolCall).status)
            l.onToolCallFinished("c1", "tap", isError = false)
            l.onToolCallFinished("c2", "swipe", isError = true)

            assertEquals("hi", (session.items.value[1] as ChatItem.Assistant).text)
            assertEquals(ToolCallStatus.DONE, (session.items.value[2] as ChatItem.ToolCall).status)
            assertEquals(ToolCallStatus.FAILED, (session.items.value[3] as ChatItem.ToolCall).status)
            gate.complete(Unit)
        }

    @Test
    fun `confirmation allow resumes the agent`() =
        runTest {
            val session = newSession()
            val (listener, gate) = holdRun()
            session.send("x")
            runCurrent()
            val answer = confirmAsync(listener.await())
            runCurrent()

            assertEquals(SessionState.AwaitingConfirmation, session.state.value)
            assertEquals(null, (session.last() as ChatItem.Confirmation).answered)
            session.answerConfirmation("c", true)
            session.answerConfirmation("c", false)
            runCurrent()

            assertTrue(answer.await())
            assertEquals(true, (session.last() as ChatItem.Confirmation).answered)
            assertEquals(SessionState.Running, session.state.value)
            gate.complete(Unit)
        }

    @Test
    fun `confirmation deny records a DENIED tool row`() =
        runTest {
            val session = newSession()
            val (listener, gate) = holdRun()
            session.send("x")
            runCurrent()
            val answer = confirmAsync(listener.await())
            runCurrent()

            session.answerConfirmation("c", false)
            session.answerConfirmation("unknown", true)
            runCurrent()

            assertFalse(answer.await())
            val last = session.last() as ChatItem.ToolCall
            assertEquals(ToolCallStatus.DENIED, last.status)
            gate.complete(Unit)
        }

    @Test
    fun `beforeToolCall collapses and waits for hidden or two seconds`() =
        runTest {
            val session = newSession()
            val (listener, gate) = holdRun()
            session.send("x")
            runCurrent()
            val l = listener.await()
            var emissions = 0
            backgroundScope.launch { session.collapseRequests.collect { emissions++ } }
            runCurrent()

            l.beforeToolCall()
            assertEquals(0, emissions)

            session.setChatVisible(true)
            val waiting = CompletableDeferred<Unit>()
            backgroundScope.launch {
                l.beforeToolCall()
                waiting.complete(Unit)
            }
            runCurrent()
            assertEquals(1, emissions)
            assertFalse(waiting.isCompleted)
            advanceTimeBy(2_001)
            runCurrent()
            assertTrue(waiting.isCompleted)
            gate.complete(Unit)
        }

    @Test
    fun `outcomes map to notice and error items`() =
        runTest {
            val session = newSession()
            agent.script = { _, _ -> AgentOutcome.StepLimitReached(5) }
            session.send("a")
            runCurrent()
            assertEquals(NoticeKind.StepLimitReached(5), (session.last() as ChatItem.Notice).kind)

            agent.script = { _, _ -> AgentOutcome.Failed(AssistantError.ModelUnavailable) }
            session.send("b")
            runCurrent()
            assertEquals(AssistantError.ModelUnavailable, (session.last() as ChatItem.Error).error)

            agent.script = { _, _ -> throw IllegalStateException("boom") }
            session.send("c")
            runCurrent()
            assertInstanceOf(AssistantError.ModelRequestFailed::class.java, (session.last() as ChatItem.Error).error)
            assertEquals(SessionState.Idle, session.state.value)
        }

    @Test
    fun `foreign CancellationException from agent returns to idle with error`() =
        runTest {
            val session = newSession()
            agent.script = { _, _ -> throw CancellationException("foreign") }

            session.send("x")
            runCurrent()

            assertInstanceOf(AssistantError.ModelRequestFailed::class.java, (session.last() as ChatItem.Error).error)
            assertEquals(SessionState.Idle, session.state.value)
        }

    @Test
    fun `stop during tool call marks rows stopped adds notice and raises no attention`() =
        runTest {
            val session = newSession()
            val (listener, _) = holdRun()
            session.send("x")
            runCurrent()
            listener.await().onToolCallStarted("c", "tap")

            session.stop()
            session.stop()
            runCurrent()

            assertEquals(ToolCallStatus.STOPPED, (session.items.value[1] as ChatItem.ToolCall).status)
            assertEquals(NoticeKind.Stopped, (session.last() as ChatItem.Notice).kind)
            assertEquals(3, session.items.value.size)
            assertEquals(SessionState.Idle, session.state.value)
            assertFalse(session.attention.value)
        }

    @Test
    fun `stop during confirmation and late callbacks are ignored`() =
        runTest {
            val session = newSession()
            val (listener, _) = holdRun()
            session.send("x")
            runCurrent()
            val l = listener.await()
            confirmAsync(l)
            runCurrent()

            session.stop()
            runCurrent()
            l.onAssistantText("late")
            l.onToolCallStarted("z", "tap")
            assertTrue(runCatching { l.confirm("d", "close_app") }.exceptionOrNull() is CancellationException)

            val confirmation = session.items.value[1] as ChatItem.Confirmation
            assertEquals(false, confirmation.answered)
            assertEquals(3, session.items.value.size)
            session.answerConfirmation("c", true)
            assertEquals(false, (session.items.value[1] as ChatItem.Confirmation).answered)
        }

    @Test
    fun `stop while beforeToolCall waits cancels the wait`() =
        runTest {
            val session = newSession()
            session.setChatVisible(true)
            var unwound = false
            agent.script = { _, l ->
                try {
                    l.beforeToolCall()
                    AgentOutcome.Completed
                } finally {
                    unwound = true
                }
            }
            session.send("x")
            runCurrent()
            assertFalse(unwound)

            session.stop()
            runCurrent()

            assertTrue(unwound)
        }

    @Test
    fun `newChat waits for the cancelled run to unwind then resets and clears`() =
        runTest {
            val session = newSession()
            val release = CompletableDeferred<Unit>()
            agent.script = { _, _ ->
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { release.await() }
                }
            }
            session.send("x")
            runCurrent()

            session.newChat()
            session.newChat()
            runCurrent()
            assertEquals(0, agent.resetCount)
            assertTrue(session.items.value.isEmpty())
            assertEquals(SessionState.Idle, session.state.value)

            release.complete(Unit)
            runCurrent()
            assertEquals(2, agent.resetCount)
            assertTrue(session.items.value.none { it is ChatItem.Notice })
        }

    @Test
    fun `disabling the assistant or clearing the key stops the run`() =
        runTest {
            val session = newSession()
            holdRun()
            session.send("x")
            runCurrent()

            configFlow.value = READY.copy(apiKeyConfigured = false)
            runCurrent()

            assertEquals(SessionState.Idle, session.state.value)
            assertEquals(NoticeKind.Stopped, (session.last() as ChatItem.Notice).kind)

            configFlow.value = READY
            runCurrent()
            holdRun()
            session.send("y")
            runCurrent()
            configFlow.value = READY.copy(enabled = false)
            runCurrent()
            assertEquals(SessionState.Idle, session.state.value)
        }

    @Test
    fun `reportError appends error raises attention and keeps state`() =
        runTest {
            val session = newSession()

            session.reportError(AssistantError.VoiceRecordingFailed)

            assertEquals(AssistantError.VoiceRecordingFailed, (session.last() as ChatItem.Error).error)
            assertTrue(session.attention.value)
            assertEquals(SessionState.Idle, session.state.value)

            session.setChatVisible(true)
            assertFalse(session.attention.value)
            session.reportError(AssistantError.ApiKeyMissing)
            assertFalse(session.attention.value)
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
