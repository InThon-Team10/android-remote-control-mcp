package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.services.accessibility.AccessibilityServiceProvider
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** Mutable parts of the UI state that are not owned by another source. */
internal data class LocalState(
    val accessibilityReady: Boolean = false,
    val permissions: PermissionSnapshot = PermissionSnapshot(),
    val busy: Boolean = false,
    val errorRes: Int? = null,
)

/**
 * The setup chain: consent, accessibility, microphone, then turning the helper on. Every step reads the stored
 * settings again, so it can be re-entered from any page; [navigate] and [currentPage] belong to the view model.
 */
class HelperSetupFlow internal constructor(
    private val settings: AssistantSettings,
    private val accessibility: AccessibilityServiceProvider,
    private val serverSwitch: HelperServerSwitch,
    private val local: MutableStateFlow<LocalState>,
    private val scope: CoroutineScope,
    private val navigate: ((HelperEntryModel) -> HelperEntryModel) -> Unit,
    private val currentPage: () -> HelperEntryPage,
) {
    /** Stores the consent, then continues the setup; a storage failure keeps the page and shows an error. */
    fun acceptConsent(context: Context) {
        scope.launch {
            if (!local.tryMarkBusy()) return@launch
            val stored =
                try {
                    settings.acceptPrivacyNotice()
                    true
                } catch (e: IOException) {
                    Logger.w(TAG, "Consent could not be stored", e)
                    local.update { it.copy(errorRes = R.string.helper_consent_error) }
                    false
                } finally {
                    local.update { it.copy(busy = false) }
                }
            if (stored) runSetup(context, consentedNow = true)
        }
    }

    /** Moves on to the first missing step; with none missing the helper is turned on. */
    fun continueSetup(context: Context) {
        scope.launch { runSetup(context, consentedNow = false) }
    }

    /** The microphone page's answer: stores the voice choice and turns the helper on. */
    fun onMicrophoneAnswered(
        context: Context,
        granted: Boolean,
    ) {
        if (currentPage() != HelperEntryPage.MICROPHONE) return
        scope.launch {
            settings.setVoiceEnabled(granted)
            turnOn(context)
        }
    }

    /** Starts the server from the visible [context], enables the helper and shows HOME. */
    fun turnOn(context: Context) {
        scope.launch {
            if (ensureServer(context)) {
                settings.setEnabled(true)
                navigate(HelperEntryModel::home)
            }
        }
    }

    suspend fun ensureServer(context: Context): Boolean {
        if (!local.tryMarkBusy()) return false
        return try {
            val result = serverSwitch.ensureRunning(context)
            local.update { it.copy(errorRes = result.exceptionOrNull()?.let(::errorFor)) }
            result.isSuccess
        } finally {
            local.update { it.copy(busy = false) }
        }
    }

    private suspend fun runSetup(
        context: Context,
        consentedNow: Boolean,
    ) {
        if (!local.tryMarkBusy()) return
        val config: AssistantConfig
        val page: HelperEntryPage
        try {
            val current = local.value
            config = settings.getConfig()
            page =
                helperSetupPage(
                    consented = config.privacyNoticeAccepted || consentedNow,
                    accessible = accessibility.isReady() || current.accessibilityReady,
                    microphoneDecided = current.permissions.microphone || config.helperIntroSeen,
                )
        } finally {
            local.update { it.copy(busy = false) }
        }
        when {
            page != HelperEntryPage.HOME -> {
                navigate { it.page(page) }
            }

            !config.apiKeyConfigured -> {
                navigate(HelperEntryModel::home)
            }

            else -> {
                if (local.value.permissions.microphone && !config.helperIntroSeen) settings.setVoiceEnabled(true)
                turnOn(context)
            }
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
        const val TAG = "MCP:HelperSetup"
    }
}

internal fun MutableStateFlow<LocalState>.tryMarkBusy(): Boolean {
    while (true) {
        val current = value
        if (current.busy) return false
        if (compareAndSet(current, current.copy(busy = true, errorRes = null))) return true
    }
}
