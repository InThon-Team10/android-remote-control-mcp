package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Mutable parts of the UI state that are not owned by another source. */
private data class LocalState(
    val accessibilityReady: Boolean = false,
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val busy: Boolean = false,
    val errorRes: Int? = null,
)

@HiltViewModel
class HelperEntryViewModel
    @Inject
    constructor(
        private val savedState: SavedStateHandle,
        private val settings: AssistantSettings,
        private val bubble: HelperBubble,
        private val starter: AssistantServerStarter,
        private val accessibility: AccessibilityServiceProvider,
    ) : ViewModel() {
        private val local = MutableStateFlow(LocalState())
        private val navigation = MutableStateFlow(savedState.restoreNavigation())

        val editor =
            HelperSettingsEditor(settings, viewModelScope) {
                local.update { it.copy(errorRes = R.string.entry_error_key_store) }
            }

        val uiState: StateFlow<HelperEntryUiState> =
            combine(
                settings.config,
                bubble.status,
                McpServerService.serverStatus,
                local,
                navigation,
            ) { config, bubbleStatus, server, local, nav ->
                HelperEntryUiState(
                    loaded = true,
                    model = nav ?: HelperEntryModel.initial(config.helperIntroSeen),
                    config = config,
                    bubble = bubbleStatus,
                    server = server,
                    accessibilityReady = local.accessibilityReady,
                    permissions = local.permissions,
                    busy = local.busy,
                    errorRes = local.errorRes,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HelperEntryUiState())

        /** Re-reads accessibility readiness and takes the permissions; on HOME while enabled it revives the server. */
        fun onResume(
            context: Context,
            permissions: PermissionSnapshot,
        ) {
            local.update { it.copy(accessibilityReady = accessibility.isReady(), permissions = permissions) }
            viewModelScope.launch {
                val config = settings.getConfig()
                val page = (navigation.value ?: HelperEntryModel.initial(config.helperIntroSeen)).page
                if (config.enabled && page == HelperEntryPage.HOME) ensureServer(context)
            }
        }

        fun startSetup() {
            navigate(HelperEntryModel::setup)
            editor.setIntroSeen(true)
        }

        fun showIntroAgain() {
            navigate(HelperEntryModel::intro)
            editor.setIntroSeen(false)
        }

        fun navigate(transition: (HelperEntryModel) -> HelperEntryModel) {
            val next = transition(uiState.value.model)
            savedState[KEY_PAGE] = next.page.name
            savedState[KEY_RETURN] = next.returnPage.name
            navigation.value = next
        }

        fun dismissError() = local.update { it.copy(errorRes = null) }

        /** "버블 켜기": starts the server from the visible [context], enables the helper and shows HOME. */
        fun turnOn(context: Context) {
            viewModelScope.launch {
                if (ensureServer(context)) {
                    settings.setEnabled(true)
                    navigate(HelperEntryModel::home)
                }
            }
        }

        fun turnOff() {
            viewModelScope.launch { settings.setEnabled(false) }
        }

        fun openInput() = bubble.openInput()

        private suspend fun ensureServer(context: Context): Boolean {
            if (!local.tryMarkBusy()) return false
            return try {
                val result = starter.ensureRunning(context)
                local.update { it.copy(errorRes = result.exceptionOrNull()?.let(::errorFor)) }
                result.isSuccess
            } finally {
                local.update { it.copy(busy = false) }
            }
        }

        private fun errorFor(failure: Throwable): Int {
            Logger.w(TAG, "Server start failed")
            return when ((failure as? AssistantException)?.error) {
                AssistantError.ServerStartBlocked -> R.string.entry_error_blocked
                else -> R.string.entry_error_start
            }
        }

        private companion object {
            const val TAG = "MCP:HelperEntry"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun MutableStateFlow<LocalState>.tryMarkBusy(): Boolean {
    while (true) {
        val current = value
        if (current.busy) return false
        if (compareAndSet(current, current.copy(busy = true, errorRes = null))) return true
    }
}

private fun SavedStateHandle.restoreNavigation(): HelperEntryModel? {
    fun page(key: String) = get<String>(key)?.let { runCatching { HelperEntryPage.valueOf(it) }.getOrNull() }
    return page(KEY_PAGE)?.let { HelperEntryModel(it, page(KEY_RETURN) ?: HelperEntryPage.HOME) }
}

private const val KEY_PAGE = "page"
private const val KEY_RETURN = "return_page"
