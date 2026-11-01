package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.WindowManager
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.mcp.tools.InFlightToolCallSignal
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AssistantOverlayHider
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperBubble
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperBubbleStatus
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperConversationControl
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperSpeaker
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The floating helper bubble and its request/answer panel. Owns the windows between [attach] and [detach]
 * (called by the accessibility service on the main thread) and reports their real state as [status].
 */
@Singleton
class HelperBubbleOverlayHost internal constructor(
    private val session: AssistantSession,
    private val settings: AssistantSettings,
    private val guidance: GuidanceController,
    private val speaker: HelperSpeaker,
    private val serverStarter: AssistantServerStarter,
    private val toolCalls: InFlightToolCallSignal,
    private val conversation: HelperConversationControl,
    private val windowsFactory: BubbleWindowsFactory,
    private val positions: BubblePositionStore,
    private val mainDispatcher: CoroutineDispatcher,
) : AccessibilityOverlayHost,
    HelperBubble {
    @Inject
    internal constructor(
        session: AssistantSession,
        settings: AssistantSettings,
        guidance: GuidanceController,
        speaker: HelperSpeaker,
        serverStarter: AssistantServerStarter,
        toolCalls: InFlightToolCallSignal,
        conversation: HelperConversationControl,
        windowsFactory: ComposeBubbleWindowsFactory,
        positions: SettingsBubblePositionStore,
    ) : this(
        session,
        settings,
        guidance,
        speaker,
        serverStarter,
        toolCalls,
        conversation,
        windowsFactory,
        positions,
        Dispatchers.Main.immediate,
    )

    private val uiState = BubbleUiState()
    private val _status = MutableStateFlow(HelperBubbleStatus.OFF)
    private val panelRequested = MutableStateFlow(false)
    private var scope: CoroutineScope? = null
    private var service: AccessibilityService? = null
    private var windows: BubbleWindowController? = null
    private val actions =
        BubbleActionsImpl(session, uiState, serverStarter, panelRequested, { scope }, { service }, positions)

    private var bubblePosition: Int? = null
    private var suppressed = false

    override val status: StateFlow<HelperBubbleStatus> = _status.asStateFlow()

    override val overlayHider: AssistantOverlayHider =
        BubbleOverlayHider(controller = { windows }, mainDispatcher = mainDispatcher)

    override fun attach(service: AccessibilityService) {
        if (scope != null) return
        Log.d(BUBBLE_TRACE, "host attached")
        this.service = service
        val newScope = CoroutineScope(SupervisorJob() + mainDispatcher)
        scope = newScope
        newScope.launch {
            // The saved position comes with the config, so the bubble is first shown where the user left it.
            combine(settings.config, positions.position, ::Pair).collect { (config, saved) ->
                bubblePosition = saved
                windows?.setBubblePosition(saved)
                onConfig(service, config.voiceEnabled, bubblePrerequisitesMet(config))
            }
        }
        newScope.launch { observeLayout() }
        newScope.launch { session.collapseRequests.collect { panelRequested.value = false } }
        newScope.launch { observeItems() }
        newScope.launch { session.state.collect { uiState.setSession(it) } }
        newScope.launch {
            combine(toolCalls.active, awaiting(), ::bubbleBusy).distinctUntilChanged().collect(uiState::setBusy)
        }
    }

    override fun detach() {
        val current = scope ?: return
        Log.d(BUBBLE_TRACE, "host detached")
        current.cancel()
        scope = null
        actions.reset()
        windows?.removeAll()
        windows = null
        service = null
        suppressed = false
        panelRequested.value = false
        session.setChatVisible(false)
        speaker.stop()
        _status.value = HelperBubbleStatus.OFF
    }

    override fun setSuppressed(suppressed: Boolean) {
        if (this.suppressed == suppressed) return
        this.suppressed = suppressed
        // A hidden focusable panel must never keep keyboard focus, so suppression also closes it.
        if (suppressed) {
            actions.cancelDictation()
            panelRequested.value = false
        }
        windows?.setSuppressed(suppressed)
    }

    override fun openInput() {
        if (_status.value != HelperBubbleStatus.ON || suppressed) return
        panelRequested.value = true
        scope?.launch { windows?.focusInput() }
    }

    private fun onConfig(
        service: AccessibilityService,
        voiceEnabled: Boolean,
        prerequisites: Boolean,
    ) {
        uiState.setVoiceEnabled(voiceEnabled)
        if (!voiceEnabled) actions.cancelDictation()
        Log.d(BUBBLE_TRACE, "gate prerequisites=$prerequisites voiceEnabled=$voiceEnabled shown=${windows != null}")
        if (!prerequisites) {
            removeWindows()
            return
        }
        if (windows != null) return
        _status.value = HelperBubbleStatus.STARTING
        val created = windowsFactory.create(service, uiState, actions)
        try {
            created.setBubblePosition(bubblePosition)
            if (suppressed) created.setSuppressed(true)
            created.showBubble()
            windows = created
            _status.value = HelperBubbleStatus.ON
            Log.d(BUBBLE_TRACE, "bubble window shown")
        } catch (error: WindowManager.BadTokenException) {
            failed(created, error)
        } catch (error: IllegalStateException) {
            failed(created, error)
        } catch (error: SecurityException) {
            failed(created, error)
        }
    }

    private fun failed(
        created: BubbleWindowController,
        error: Exception,
    ) {
        Logger.e(TAG, "Could not add the helper bubble window: ${error.javaClass.simpleName}")
        created.removeAll()
        _status.value = HelperBubbleStatus.FAILED
    }

    private fun removeWindows() {
        actions.cancelDictation()
        if (windows != null) Log.d(BUBBLE_TRACE, "bubble windows removed")
        windows?.removeAll()
        windows = null
        panelRequested.value = false
        session.setChatVisible(false)
        _status.value = HelperBubbleStatus.OFF
    }

    private suspend fun observeLayout() {
        val guidanceActive = guidance.state.map { it != null }.distinctUntilChanged()
        combine(panelRequested, guidanceActive, session.state) { requested, guided, sessionState ->
            resolveLayout(requested, guided, thinking = sessionState == SessionState.Running)
        }.distinctUntilChanged()
            .collect { layout ->
                Log.d(BUBBLE_TRACE, "layout $layout")
                if (!layout.panelOpen) actions.cancelDictation()
                uiState.setLayout(layout)
                windows?.setThinking(layout.thinking)
                windows?.setPanelOpen(layout.panelOpen)
                windows?.setBubbleVisible(layout.bubbleVisible)
                session.setChatVisible(layout.panelOpen)
            }
    }

    private fun awaiting(): Flow<Boolean> =
        combine(session.items, session.state, conversation.active, ::awaitingTextAnswer).distinctUntilChanged()

    private suspend fun observeItems() {
        session.items.collect(uiState::setItems)
    }

    private companion object {
        const val TAG = "MCP:HelperBubble"
    }
}

/** Content-free trace of the bubble lifecycle (debugging the bubble and voice session together). */
internal const val BUBBLE_TRACE = "MCP:BubbleTrace"
