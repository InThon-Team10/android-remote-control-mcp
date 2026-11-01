package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.errorTextRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HelperEntryViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val settings: AssistantSettings,
        private val bubble: HelperBubble,
        private val serverSwitch: HelperServerSwitch,
        private val accessibility: AccessibilityServiceProvider,
        private val conversation: HelperConversationControl,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState())
        private val navigation = MutableStateFlow(savedState.restoreNavigation())

        val editor = HelperSettingsEditor(settings, viewModelScope)

        val setup =
            HelperSetupFlow(
                settings = settings,
                accessibility = accessibility,
                serverSwitch = serverSwitch,
                local = local,
                scope = viewModelScope,
                navigate = ::navigate,
                currentPage = { (navigation.value ?: HelperEntryModel.initial(false)).page },
            )

        val uiState: StateFlow<HelperEntryUiState> =
            combine(
                settings.config,
                McpServerService.serverStatus,
                local,
                navigation,
                conversation.active,
            ) { config, server, local, nav, conversationActive ->
                HelperEntryUiState(
                    loaded = true,
                    model = nav ?: HelperEntryModel.initial(config.helperIntroSeen),
                    config = config,
                    server = server,
                    accessibilityReady = local.accessibilityReady,
                    permissions = local.permissions,
                    busy = local.busy,
                    errorRes = local.errorRes,
                    conversationActive = conversationActive,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HelperEntryUiState())

        /**
         * Re-reads accessibility readiness and takes the permissions; on HOME while enabled it revives the server and
         * on the permission page it continues the setup as soon as accessibility is on.
         */
        fun onResume(
            context: Context,
            permissions: PermissionSnapshot,
        ) {
            val ready = accessibility.isReady()
            local.update { it.copy(accessibilityReady = ready, permissions = permissions) }
            viewModelScope.launch {
                val config = settings.getConfig()
                val page = (navigation.value ?: HelperEntryModel.initial(config.helperIntroSeen)).page
                when {
                    page == HelperEntryPage.PERMISSION && ready -> setup.continueSetup(context)
                    config.enabled && page == HelperEntryPage.HOME -> setup.ensureServer(context)
                }
            }
        }

        /** Keeps the floating 도움 버튼 hidden on every page but HOME; call while the activity is resumed. */
        suspend fun suppressBubbleOffHome() {
            try {
                uiState
                    .filter { it.loaded }
                    .map { it.model.page != HelperEntryPage.HOME }
                    .distinctUntilChanged()
                    .collect { bubble.setSuppressed(it) }
            } finally {
                bubble.setSuppressed(false)
            }
        }

        fun startPractice() = endConversationThen(HelperEntryModel::practice)

        fun showIntroAgain() {
            viewModelScope.launch {
                conversation.stop()
                editor.setIntroSeen(false)
                navigate(HelperEntryModel::intro)
            }
        }

        /** The live conversation must be over before the practice or the intro owns the microphone and the screen. */
        private fun endConversationThen(transition: (HelperEntryModel) -> HelperEntryModel) {
            viewModelScope.launch {
                conversation.stop()
                navigate(transition)
            }
        }

        fun navigate(transition: (HelperEntryModel) -> HelperEntryModel) {
            val next = transition(uiState.value.model)
            savedState[KEY_PAGE] = next.page.name
            savedState[KEY_STEP] = next.step.name
            navigation.value = next
            if (next.page == HelperEntryPage.HOME && !uiState.value.config.helperIntroSeen) editor.setIntroSeen(true)
        }

        fun dismissError() = local.update { it.copy(errorRes = null) }

        fun reportError(
            @StringRes errorRes: Int,
        ) = local.update { it.copy(errorRes = errorRes) }

        /** "도우미 종료": ends the conversation, disables the helper and stops the server. */
        fun turnOff(context: Context) {
            viewModelScope.launch {
                conversation.stop()
                settings.setEnabled(false)
                serverSwitch.stop(context)
            }
        }

        /** Starts the live conversation; [onStarted] runs only after it started, a failure is shown as an error. */
        fun openHelper(onStarted: () -> Unit) {
            viewModelScope.launch {
                local.update { it.copy(errorRes = null) }
                conversation
                    .start()
                    .onSuccess { onStarted() }
                    .onFailure { failure ->
                        val error = (failure as? AssistantException)?.error
                        local.update {
                            it.copy(errorRes = error?.let(::errorTextRes) ?: R.string.bubble_error_generic)
                        }
                    }
            }
        }

        fun openInput() = bubble.openInput()

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun SavedStateHandle.restoreNavigation(): HelperEntryModel? {
    fun page(key: String) = get<String>(key)?.let { runCatching { HelperEntryPage.valueOf(it) }.getOrNull() }
    val step = get<String>(KEY_STEP)?.let { runCatching { HelperPracticeStep.valueOf(it) }.getOrNull() }
    return page(KEY_PAGE)?.let { HelperEntryModel(it, step ?: HelperPracticeStep.OPEN) }
}

private const val KEY_PAGE = "page"
private const val KEY_STEP = "practice_step"
