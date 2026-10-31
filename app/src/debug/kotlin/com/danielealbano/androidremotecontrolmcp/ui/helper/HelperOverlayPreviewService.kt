package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiErrorMapper
import com.danielealbano.androidremotecontrolmcp.services.accessibility.OWN_OVERLAY_ACCESSIBILITY_TITLE
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Debug-only overlay host. It neither reads window content nor executes gestures.
 * A separate non-touchable accessibility window draws the active frame behind the input bar.
 */
@AndroidEntryPoint
@Suppress("MagicNumber", "TooManyFunctions")
class HelperOverlayPreviewService :
    AccessibilityService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val windows = mutableListOf<ComposeView>()
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }

    @Inject internal lateinit var assistantConnection: HelperAssistantConnection
    private val assistantScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var requestJob: Job? = null
    private var requestEpoch = 0
    private var busy by mutableStateOf(false)
    private var requestMessage by mutableStateOf<String?>(null)
    private var request by mutableStateOf("")
    private var voiceListening by mutableStateOf(false)
    private var voiceRecognizing by mutableStateOf(false)
    private var voiceStatus by mutableStateOf<String?>(null)
    private var permissionEpoch = 0
    private val voiceInput by lazy {
        HelperVoiceInput(
            this,
            onTranscript = { request = it },
            onState = { listening, recognizing, message ->
                voiceListening = listening
                voiceRecognizing = recognizing
                voiceStatus = message
            },
        )
    }
    private var speech: TextToSpeech? = null
    private var speechReady by mutableStateOf(false)
    private var menuExpanded = false
    private var menuOpenOrigin: android.graphics.PointF? = null
    private var guideControlsHeight = 0
    private var guideCaptionFits = true
    private var guidePanel = false
    private var renderGeneration = 0
    private var guideHidden = false
    private var menuCorner = 0
    private var paused = false
    private var overlayConnected = false
    private var expanded = false
    private var guiding by mutableStateOf(false)
    private val consequenceHost by lazy {
        ConsequenceGuideHost(
            this,
            ::clearWindows,
            ::attach,
            ::showBubble,
            speak = { text ->
                speech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "consequence") == TextToSpeech.SUCCESS
            },
            stopSpeech = { speech?.stop() },
            speechReady = { speechReady },
        )
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        speech =
            TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    speechReady = (speech?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= 0
                }
            }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        overlayConnected = true
        consequenceHost.connect()
        HelperRuntime.connect()
        HelperRuntime.show = {
            paused = false
            showBubble()
        }
        HelperRuntime.ask = ::showSpeech
        HelperRuntime.hide = {
            paused = true
            cancelRequest()
            consequenceHost.finish()
            speech?.stop()
            expanded = false
            clearWindows()
        }
        showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && event.packageName?.toString() != packageName) consequenceHost.invalidateLive()
    }

    private fun cancelVoiceInput() {
        permissionEpoch++
        voiceInput.cancel()
    }

    private fun toggleVoiceInput() {
        if (busy) return
        if (voiceListening) {
            voiceInput.stop()
            return
        }
        if (voiceRecognizing) return
        speech?.stop()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            voiceInput.start(request)
            return
        }
        val token = ++permissionEpoch
        voiceRecognizing = true
        voiceStatus = getString(R.string.helper_voice_permission)
        val receiver =
            object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(
                    resultCode: Int,
                    resultData: Bundle?,
                ) {
                    if (token != permissionEpoch || !overlayConnected || !expanded || paused || HelperRuntime.suppressed) return
                    voiceRecognizing = false
                    if (resultCode == Activity.RESULT_OK &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    ) {
                        voiceInput.start(request)
                    } else {
                        voiceStatus = getString(R.string.helper_voice_permission_denied)
                    }
                }
            }
        try {
            startActivity(
                Intent(this, HelperVoicePermissionActivity::class.java)
                    .putExtra(HelperVoicePermissionActivity.EXTRA_RESULT, receiver)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: RuntimeException) {
            voiceRecognizing = false
            voiceStatus = getString(R.string.helper_voice_permission_denied)
        }
    }

    private fun cancelRequest() {
        requestEpoch++
        requestJob?.cancel()
        requestJob = null
        busy = false
    }

    private fun submitRequest() {
        if (busy || voiceRecognizing || request.isBlank()) return
        cancelRequest()
        consequenceHost.finish()
        val token = requestEpoch
        busy = true
        requestMessage = "현재 화면을 읽고 안내를 준비하고 있어요. X를 누르면 중단해요."
        showSpeech()
        requestJob =
            assistantScope.launch {
                try {
                    delay(350)
                    val answer = assistantConnection.request(this@HelperOverlayPreviewService, request)
                    if (token != requestEpoch || !overlayConnected) return@launch
                    busy = false
                    requestMessage = null
                    consequenceHost.beginLive(answer.step, ::submitRequest) { locate ->
                        assistantScope.launch {
                            try {
                                if (token == requestEpoch && assistantConnection.readScreen().signature == answer.signature) {
                                    locate()
                                } else {
                                    consequenceHost.invalidateLive()
                                }
                            } catch (
                                cancelled: CancellationException,
                            ) {
                                throw cancelled
                            } catch (_: Exception) {
                                consequenceHost.invalidateLive()
                            }
                        }
                    }
                    // Events invalidate immediately; polling also covers apps that omit accessibility events.
                    while (isActive && token == requestEpoch && consequenceHost.live) {
                        delay(1000)
                        if (assistantConnection.readScreen().signature != answer.signature) {
                            consequenceHost.invalidateLive()
                            break
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    if (token == requestEpoch && overlayConnected) {
                        busy = false
                        consequenceHost.finish()
                        requestMessage = helperConnectionMessage(error)
                        showSpeech()
                    }
                }
            }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        overlayConnected = false
        HelperRuntime.disconnect()
        cancelRequest()
        consequenceHost.disconnect()
        clearWindows()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        cancelVoiceInput()
        speech?.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (paused || HelperRuntime.suppressed) return
        if (ConsequencePreviewBridge.active) {
            consequenceHost.refresh()
        } else if (expanded) {
            showSpeech()
        } else {
            showBubble()
        }
    }

    private fun showBubble() {
        if (HelperRuntime.suppressed || paused) return
        cancelRequest()
        requestMessage = null
        consequenceHost.finish()
        windows.forEach { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
        speech?.stop()
        expanded = false
        menuExpanded = false
        menuOpenOrigin = null
        guidePanel = false
        guideCaptionFits = true
        guideControlsHeight = 0
        guideHidden = false
        guiding = false
        clearWindows()
        val params = windowParams(dp(76), dp(92), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        params.gravity = Gravity.TOP or Gravity.END
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        params.x = insets.right
        params.y = insets.top + (metrics.bounds.height() - insets.top - insets.bottom) / 3
        attach(params) { HelperEdgeCharacter(onExpand = { showSpeech() }) }
    }

    @Suppress("DEPRECATION") // Service window uses resize behavior for its inline text field.
    private fun showSpeech() {
        expanded = true
        clearWindows()
        val token = renderGeneration
        val metrics = windowManager.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val usableHeight = metrics.bounds.height() - insets.top - insets.bottom
        val targetTop = (usableHeight * 0.095f).toInt() - dp(32)
        val targetBottom = targetTop + dp(64)
        val controlsTop = helperControlsTop(usableHeight, targetTop, targetBottom, guideControlsHeight, dp(16), menuCorner >= 2)
        guidePanel = guiding && !guideHidden && ((!guideCaptionFits) || (guideControlsHeight > 0 && controlsTop == null))
        if (!guiding || !guideHidden) {
            attach(
                windowParams(
                    metrics.bounds.width() - insets.left - insets.right,
                    usableHeight,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                ).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = insets.left
                    y = insets.top
                },
            ) {
                HelperOverlayFrame(
                    guiding = guiding,
                    captionVisible = !guidePanel && !menuExpanded,
                    highlightVisible = guiding && !guidePanel && !menuExpanded,
                    captionBottomInset = if (controlsTop != dp(16)) guideControlsHeight + dp(32) else 0,
                    captionTopInset = if (controlsTop == dp(16)) guideControlsHeight + dp(32) else 0,
                    onCaptionFit = { fits ->
                        if (guiding && fits != guideCaptionFits) {
                            windows.lastOrNull()?.post {
                                if (expanded && guiding && token == renderGeneration) {
                                    guideCaptionFits = fits
                                    showSpeech()
                                }
                            }
                        }
                    },
                )
            }
        }
        if (guiding) {
            val closeParams =
                windowParams(
                    if (menuExpanded || guidePanel) {
                        windowManager.currentWindowMetrics.bounds.width()
                    } else {
                        WindowManager.LayoutParams.WRAP_CONTENT
                    },
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                )
            closeParams.gravity =
                if (menuExpanded || guidePanel) {
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                } else {
                    Gravity.TOP or (if (menuCorner == 1 || menuCorner == 2) Gravity.START else Gravity.END)
                }
            closeParams.x = if (menuExpanded || guidePanel) 0 else dp(16)
            closeParams.y = if (menuExpanded || guidePanel) dp(12) + insets.bottom else (controlsTop ?: dp(16)) + insets.top
            attach(closeParams) {
                Box(
                    Modifier
                        .widthIn(
                            max = ((metrics.bounds.width() - insets.left - insets.right) / resources.displayMetrics.density - 32).dp,
                        ).onSizeChanged { size ->
                            if (!menuExpanded && !guidePanel && size.height != guideControlsHeight) {
                                windows.lastOrNull()?.post {
                                    if (expanded && guiding && token == renderGeneration) {
                                        guideControlsHeight = size.height
                                        showSpeech()
                                    }
                                }
                            }
                        },
                ) {
                    HelperGuideControls(
                        menuExpanded = menuExpanded,
                        guideHidden = guideHidden,
                        fallback = guidePanel,
                        onToggleMenu = {
                            if (!menuExpanded) {
                                windows.lastOrNull()?.let { view ->
                                    val location = IntArray(2)
                                    view.getLocationOnScreen(location)
                                    menuOpenOrigin =
                                        android.graphics.PointF(
                                            location[0] + dp(8) + (view.width - dp(88)).coerceAtLeast(0) / 2f,
                                            location[1] + view.height / 2f,
                                        )
                                }
                            }
                            menuExpanded = !menuExpanded
                            showSpeech()
                        },
                        onHide = {
                            speech?.stop()
                            guideHidden = !guideHidden
                            menuExpanded = false
                            showSpeech()
                        },
                        onMove = {
                            menuCorner = (menuCorner + 1) % 4
                            menuExpanded = false
                            showSpeech()
                        },
                        onRetry = {
                            speech?.stop()
                            guiding = false
                            menuExpanded = false
                            guideHidden = false
                            showSpeech()
                        },
                        onCollapse = { showBubble() },
                        onExit = {
                            speech?.stop()
                            clearWindows()
                            disableSelf()
                        },
                        onSpeak = {
                            val result =
                                speech?.speak(
                                    getString(R.string.helper_overlay_search_instruction),
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "guide",
                                )
                            if (result == TextToSpeech.ERROR) speechReady = false
                        },
                        speechReady = speechReady,
                        maxHeightDp = (usableHeight / resources.displayMetrics.density - 56).toInt(),
                    )
                }
            }
            return
        }
        val bounds = windowManager.currentWindowMetrics.bounds
        val params =
            windowParams(
                minOf(bounds.width() - insets.left - insets.right, dp(420)),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or (if (busy) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE else 0),
            )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = dp(12) + insets.bottom
        params.setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        attach(params) {
            HelperOverlaySpeech(
                request = request,
                onRequestChange = {
                    cancelVoiceInput()
                    request = it
                },
                isListening = voiceListening,
                isRecognizing = voiceRecognizing,
                voiceStatus = voiceStatus,
                onVoiceToggle = ::toggleVoiceInput,
                onCollapse = { showBubble() },
                guiding = guiding,
                busy = busy,
                message = requestMessage,
                onSubmit = ::submitRequest,
                onTest = {
                    showBubble()
                    startActivity(
                        Intent(this, ConsequencePreviewActivity::class.java)
                            .putExtra(ConsequencePreviewActivity.EXTRA_START_TEST, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                },
                maxHeightDp = (usableHeight / resources.displayMetrics.density * 0.65f).toInt(),
            )
        }
    }

    private fun windowParams(
        width: Int,
        height: Int,
        flags: Int,
    ): WindowManager.LayoutParams =
        WindowManager
            .LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { setFitInsetsTypes(0) }

    private fun attach(
        params: WindowManager.LayoutParams,
        content: @Composable () -> Unit,
    ) {
        if (!overlayConnected || HelperRuntime.suppressed || paused) return
        params.setTitle(OWN_OVERLAY_ACCESSIBILITY_TITLE)
        val view =
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@HelperOverlayPreviewService)
                setViewTreeSavedStateRegistryOwner(this@HelperOverlayPreviewService)
                setContent { AndroidRemoteControlMcpTheme(dynamicColor = false, content = content) }
            }
        try {
            windowManager.addView(view, params)
        } catch (error: WindowManager.BadTokenException) {
            // The system can revoke the accessibility token before onUnbind is delivered.
            Log.w("HelperOverlay", "Overlay connection ended", error)
            view.disposeComposition()
            overlayConnected = false
            HelperRuntime.disconnect()
            consequenceHost.disconnect()
            clearWindows()
            return
        }
        windows.add(view)
        HelperRuntime.visible = true
        val origin = menuOpenOrigin
        if (menuExpanded && windows.size > 1 && origin != null) {
            menuOpenOrigin = null
            if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                view.addOnLayoutChangeListener(
                    object : android.view.View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: android.view.View,
                            left: Int,
                            top: Int,
                            right: Int,
                            bottom: Int,
                            oldLeft: Int,
                            oldTop: Int,
                            oldRight: Int,
                            oldBottom: Int,
                        ) {
                            if (v.width == 0 || v.height == 0) return
                            v.removeOnLayoutChangeListener(this)
                            val location = IntArray(2)
                            v.getLocationOnScreen(location)
                            v.pivotX = v.width / 2f
                            v.pivotY = v.height / 2f
                            val startX = origin.x - location[0] - v.width / 2f
                            val startY = origin.y - location[1] - v.height / 2f
                            val restingY = params.y
                            android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = 240
                                interpolator = android.view.animation.DecelerateInterpolator()
                                addUpdateListener { animation ->
                                    if (!v.isAttachedToWindow) {
                                        animation.cancel()
                                        return@addUpdateListener
                                    }
                                    val progress = animation.animatedValue as Float
                                    params.x = (startX * (1f - progress)).toInt()
                                    params.y = restingY - (startY * (1f - progress)).toInt()
                                    v.scaleX = 0.15f + 0.85f * progress
                                    v.scaleY = v.scaleX
                                    v.alpha = progress
                                    windowManager.updateViewLayout(v, params)
                                }
                                start()
                            }
                        }
                    },
                )
            }
        }
    }

    private fun clearWindows() {
        cancelVoiceInput()
        renderGeneration++
        windows.forEach {
            windowManager.removeView(it)
            it.disposeComposition()
        }
        windows.clear()
        HelperRuntime.visible = false
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        overlayConnected = false
        HelperRuntime.disconnect()
        cancelRequest()
        consequenceHost.disconnect()
        speech?.stop()
        speech?.shutdown()
        clearWindows()
        assistantScope.cancel()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }
}

private fun helperConnectionMessage(error: Exception): String {
    if (error is HelperGuideException) return error.message.orEmpty()
    return when (GeminiErrorMapper.map(error)) {
        AssistantError.GeminiAuthFailed, AssistantError.ApiKeyMissing -> "Gemini API 키를 확인해 주세요. AI 연결 설정에서 바꿀 수 있어요."
        AssistantError.GeminiQuotaExceeded -> "Gemini 사용 한도에 도달했어요. 사용량을 확인한 뒤 다시 요청해 주세요."
        AssistantError.GeminiUnavailable -> "AI에 연결할 수 없어요. 인터넷 연결을 확인하고 다시 요청해 주세요."
        AssistantError.BearerDisabled -> "MCP 앱 설정에서 Bearer 인증을 켜 주세요."
        AssistantError.ServerStartBlocked, is AssistantError.ServerStartFailed -> "MCP 앱에서 서버를 시작한 뒤 다시 요청해 주세요."
        is AssistantError.McpUnavailable -> "MCP 연결을 확인해 주세요. 서버와 화면 읽기 도구가 켜져 있어야 해요."
        else -> "안내를 가져오지 못했어요. AI 연결 설정과 현재 화면을 확인하고 다시 요청해 주세요."
    }
}
