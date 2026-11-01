package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.mcp.tools.InFlightToolCallSignal
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceState
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperBubbleStatus
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperConversationControl
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperSpeaker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelperBubbleOverlayHostTest {
    private val ready = AssistantConfig(enabled = true, privacyNoticeAccepted = true, apiKeyConfigured = true)
    private val config = MutableStateFlow(ready)
    private val items = MutableStateFlow<List<ChatItem>>(emptyList())
    private val guidanceState = MutableStateFlow<GuidanceState?>(null)
    private val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    private val session =
        mockk<AssistantSession>(relaxed = true) {
            every { items } returns this@HelperBubbleOverlayHostTest.items
            every { state } returns sessionState
            every { collapseRequests } returns MutableSharedFlow()
        }
    private val settings = mockk<AssistantSettings> { every { config } returns this@HelperBubbleOverlayHostTest.config }
    private val guidance = mockk<GuidanceController> { every { state } returns guidanceState }
    private val speaker = mockk<HelperSpeaker>(relaxed = true)
    private val toolCalls = InFlightToolCallSignal()
    private val liveActive = MutableStateFlow(false)
    private val conversation = mockk<HelperConversationControl> { every { active } returns liveActive }
    private val controller = mockk<BubbleWindowController>(relaxed = true)
    private val service = mockk<AccessibilityService>()
    private var created = 0
    private val savedPosition = MutableStateFlow<Int?>(null)
    private val saves = mutableListOf<Int>()
    private val positions =
        object : BubblePositionStore {
            override val position = savedPosition

            override suspend fun save(permille: Int) {
                saves += permille
                savedPosition.value = permille
            }
        }

    private fun TestScope.host() =
        HelperBubbleOverlayHost(
            session,
            settings,
            guidance,
            speaker,
            mockk<AssistantServerStarter>(),
            toolCalls,
            conversation,
            { _, _, _ ->
                created++
                controller
            },
            positions,
            StandardTestDispatcher(testScheduler),
        )

    @Test
    fun `status is OFF before attach and ON once the bubble window is added`() =
        runTest {
            val host = host()
            assertEquals(HelperBubbleStatus.OFF, host.status.value)

            host.attach(service)
            advanceUntilIdle()

            assertEquals(HelperBubbleStatus.ON, host.status.value)
            verify(exactly = 1) { controller.showBubble() }
        }

    @Test
    fun `attach twice never creates a second window set`() =
        runTest {
            val host = host()

            host.attach(service)
            host.attach(service)
            advanceUntilIdle()

            assertEquals(1, created)
        }

    @Test
    fun `missing prerequisites keep the status OFF and remove the windows when lost`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()

            config.value = ready.copy(apiKeyConfigured = false)
            advanceUntilIdle()

            assertEquals(HelperBubbleStatus.OFF, host.status.value)
            verify { controller.removeAll() }
        }

    @Test
    fun `a window manager failure sets FAILED`() =
        runTest {
            every { controller.showBubble() } throws IllegalStateException("no window")
            val host = host()

            host.attach(service)
            advanceUntilIdle()

            assertEquals(HelperBubbleStatus.FAILED, host.status.value)
        }

    @Test
    fun `a bad token sets FAILED`() =
        runTest {
            every { controller.showBubble() } throws WindowManager.BadTokenException()
            val host = host()

            host.attach(service)
            advanceUntilIdle()

            assertEquals(HelperBubbleStatus.FAILED, host.status.value)
        }

    @Test
    fun `detach is idempotent, removes windows and returns to OFF`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()

            host.detach()
            host.detach()

            assertEquals(HelperBubbleStatus.OFF, host.status.value)
            verify(exactly = 1) { controller.removeAll() }
        }

    @Test
    fun `openInput expands the panel only when ON and tells the session the chat is visible`() =
        runTest {
            val host = host()
            host.openInput()
            host.attach(service)
            advanceUntilIdle()
            verify(exactly = 0) { controller.setPanelOpen(true) }

            host.openInput()
            advanceUntilIdle()

            verify { controller.setPanelOpen(true) }
            verify { session.setChatVisible(true) }
        }

    @Test
    fun `suppression hides every window, closes the panel and survives the screenshot hider`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()
            host.openInput()
            advanceUntilIdle()

            host.setSuppressed(true)
            host.setSuppressed(true)
            advanceUntilIdle()
            host.overlayHider.hiddenDuring { }

            verify(exactly = 1) { controller.setSuppressed(true) }
            verify { controller.setPanelOpen(false) }
            verify(exactly = 0) { controller.setSuppressed(false) }
            host.openInput()
            advanceUntilIdle()
            verify(exactly = 1) { controller.setPanelOpen(true) }
        }

    @Test
    fun `suppression applies to a window added later`() =
        runTest {
            config.value = ready.copy(apiKeyConfigured = false)
            val host = host()
            host.attach(service)
            advanceUntilIdle()
            host.setSuppressed(true)

            config.value = ready
            advanceUntilIdle()

            verify { controller.setSuppressed(true) }
        }

    @Test
    fun `detach clears suppression`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()
            host.setSuppressed(true)
            host.detach()

            host.attach(service)
            advanceUntilIdle()

            verify(exactly = 1) { controller.setSuppressed(true) }
            verify(exactly = 0) { controller.setSuppressed(false) }
        }

    @Test
    fun `guidance collapses the panel and hides the bubble, then restores`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()
            host.openInput()
            advanceUntilIdle()

            guidanceState.value = mockk<GuidanceState>()
            advanceUntilIdle()
            verify { controller.setBubbleVisible(false) }
            verify { controller.setPanelOpen(false) }

            guidanceState.value = null
            advanceUntilIdle()
            verify { controller.setPanelOpen(true) }
        }

    @Test
    fun `new assistant text is spoken once and old text is not replayed`() =
        runTest {
            items.value = listOf(ChatItem.Assistant(1, "old"))
            val host = host()
            host.attach(service)
            advanceUntilIdle()
            verify(exactly = 0) { speaker.speak(any()) }

            items.value = items.value + ChatItem.Assistant(2, "new")
            advanceUntilIdle()

            verify(exactly = 0) { speaker.speak(any()) }
        }

    @Test
    fun `the saved bubble position is applied before the bubble is shown and when it changes`() =
        runTest {
            savedPosition.value = 420
            val host = host()

            host.attach(service)
            advanceUntilIdle()
            savedPosition.value = 610
            advanceUntilIdle()

            verifyOrder {
                controller.setBubblePosition(420)
                controller.showBubble()
                controller.setBubblePosition(610)
            }
        }

    @Test
    fun `busy label follows in-flight tool calls`() =
        runTest {
            var state: BubbleUiState? = null
            val host =
                HelperBubbleOverlayHost(
                    session,
                    settings,
                    guidance,
                    speaker,
                    mockk<AssistantServerStarter>(),
                    toolCalls,
                    conversation,
                    { _, ui, _ ->
                        state = ui
                        controller
                    },
                    positions,
                    StandardTestDispatcher(testScheduler),
                )
            host.attach(service)
            advanceUntilIdle()
            sessionState.value = SessionState.Running
            liveActive.value = true
            advanceUntilIdle()
            assertEquals(false, state!!.busy.value)

            toolCalls.onToolCallStarted("tap")
            advanceUntilIdle()
            assertEquals(true, state!!.busy.value)

            toolCalls.onToolCallFinished("tap")
            advanceUntilIdle()
            assertEquals(false, state!!.busy.value)
        }

    @Test
    fun `thinking window shows for a pending request and is removed when a step appears`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()

            items.value = listOf(ChatItem.User(1, "q"))
            sessionState.value = SessionState.Running
            advanceUntilIdle()
            guidanceState.value = mockk<GuidanceState>()
            advanceUntilIdle()

            verifyOrder {
                controller.setThinking(true)
                controller.setThinking(false)
            }
            verify { controller.setBubbleVisible(false) }
        }

    @Test
    fun `moving the bubble saves its position`() =
        runTest {
            var actions: BubbleActions? = null
            val host =
                HelperBubbleOverlayHost(
                    session,
                    settings,
                    guidance,
                    speaker,
                    mockk<AssistantServerStarter>(),
                    toolCalls,
                    conversation,
                    { _, _, a ->
                        actions = a
                        controller
                    },
                    positions,
                    StandardTestDispatcher(testScheduler),
                )
            host.attach(service)
            advanceUntilIdle()

            actions!!.onBubbleMoved(333)
            advanceUntilIdle()

            assertEquals(listOf(333), saves)
        }

    @Test
    fun `thinking returns between steps even after assistant text without reopening chat`() =
        runTest {
            val host = host()
            host.attach(service)
            advanceUntilIdle()
            host.openInput()
            sessionState.value = SessionState.Running
            items.value = listOf(ChatItem.User(1, "q"), ChatItem.Assistant(2, "intermediate"))
            advanceUntilIdle()
            guidanceState.value = mockk<GuidanceState>()
            advanceUntilIdle()
            guidanceState.value = null
            advanceUntilIdle()
            verifyOrder {
                controller.setThinking(true)
                controller.setThinking(false)
                controller.setThinking(true)
                controller.setPanelOpen(false)
            }
        }
}
