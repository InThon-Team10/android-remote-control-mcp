package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.WindowManager
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceSession
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import com.danielealbano.androidremotecontrolmcp.assistant.voice.VOICE_TRACE
import com.danielealbano.androidremotecontrolmcp.assistant.voice.isActive
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityOverlayHost
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AssistantOverlayHider
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperBubble
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperBubbleStatus
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperSpeaker
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val liveVoice: LiveVoiceSession,
    private val serverStarter: AssistantServerStarter,
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
        liveVoice: LiveVoiceSession,
        serverStarter: AssistantServerStarter,
        windowsFactory: ComposeBubbleWindowsFactory,
        positions: SettingsBubblePositionStore,
    ) : this(
        session,
        settings,
        guidance,
        speaker,
        liveVoice,
        serverStarter,
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
        BubbleActionsImpl(session, liveVoice, serverStarter, panelRequested, { scope }, { service }, positions)

    private var bubblePosition: Int? = null

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
            liveVoice.state
                .map { it.isActive() }
                .distinctUntilChanged()
                .collect {
                    Log.d(BUBBLE_TRACE, "live voice active=$it (bubble status=${_status.value})")
                    uiState.setListening(it)
                }
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
        panelRequested.value = false
        session.setChatVisible(false)
        speaker.stop()
        _status.value = HelperBubbleStatus.OFF
    }

    override fun openInput() {
        if (_status.value != HelperBubbleStatus.ON) return
        panelRequested.value = true
        scope?.launch { windows?.focusInput() }
    }

    private fun onConfig(
        service: AccessibilityService,
        voiceEnabled: Boolean,
        prerequisites: Boolean,
    ) {
        uiState.setVoiceEnabled(voiceEnabled)
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
        if (windows != null) Log.d(BUBBLE_TRACE, "bubble windows removed")
        windows?.removeAll()
        windows = null
        panelRequested.value = false
        session.setChatVisible(false)
        _status.value = HelperBubbleStatus.OFF
    }

    private suspend fun observeLayout() {
        combine(panelRequested, guidance.state.map { it != null }.distinctUntilChanged(), ::resolveLayout)
            .distinctUntilChanged()
            .collect { layout ->
                Log.d(BUBBLE_TRACE, "layout panelOpen=${layout.panelOpen} bubbleVisible=${layout.bubbleVisible}")
                uiState.setLayout(layout)
                windows?.setPanelOpen(layout.panelOpen)
                windows?.setBubbleVisible(layout.bubbleVisible)
                session.setChatVisible(layout.panelOpen)
            }
    }

    private suspend fun observeItems() {
        var lastSpoken = latestAssistantId(session.items.value)
        session.items.collect { items ->
            uiState.setItems(items)
            newAssistantText(items, lastSpoken)?.let { (id, text) ->
                lastSpoken = id
                Log.d(VOICE_TRACE, "bubble speaks new answer item id=$id len=${text.length}")
                speaker.speak(text)
            }
        }
    }

    private companion object {
        const val TAG = "MCP:HelperBubble"
    }
}

/** Content-free trace of the bubble lifecycle (debugging the bubble and voice session together). */
internal const val BUBBLE_TRACE = "MCP:BubbleTrace"
