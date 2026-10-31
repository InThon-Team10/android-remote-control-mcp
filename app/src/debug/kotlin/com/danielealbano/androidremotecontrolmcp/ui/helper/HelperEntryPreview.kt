@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val EntrySaver =
    listSaver<HelperEntryModel, String>(
        save = { listOf(it.page.name, it.step.name) },
        restore = { HelperEntryModel(HelperEntryPage.valueOf(it[0]), HelperPracticeStep.valueOf(it[1])) },
    )

@Composable
fun HelperEntryPreview(
    settings: AssistantSettings,
    onOpenPlayground: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("helper_onboarding", Context.MODE_PRIVATE) }
    var model by rememberSaveable(stateSaver = EntrySaver) {
        mutableStateOf(
            HelperEntryModel(page = if (preferences.getBoolean("finished", false)) HelperEntryPage.HOME else HelperEntryPage.WELCOME),
        )
    }
    var permissionError by rememberSaveable { mutableStateOf(false) }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    var setupBusy by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun continueSetup() {
        if (setupBusy) return
        setupBusy = true
        setupError = false
        scope.launch {
            try {
                val config = settings.getConfig()
                val micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                model =
                    model.copy(
                        page =
                            helperSetupPage(
                                config.privacyNoticeAccepted,
                                HelperRuntime.connected,
                                micGranted || preferences.getBoolean("microphone_choice_done", false),
                            ),
                    )
                if (model.page == HelperEntryPage.HOME) {
                    settings.setEnabled(true)
                    HelperRuntime.suppress(false)
                    HelperRuntime.show?.invoke()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                setupError = true
                model = model.copy(page = HelperEntryPage.CONSENT)
            } finally {
                setupBusy = false
            }
        }
    }
    val microphone =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            preferences.edit().putBoolean("microphone_choice_done", true).apply()
            continueSetup()
        }
    DisposableEffect(lifecycle, model.page) {
        fun refresh() {
            HelperRuntime.suppress(model.page != HelperEntryPage.HOME)
        }
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refresh()
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        onDispose {
            lifecycle.removeObserver(observer)
            HelperRuntime.suppress(false)
        }
    }
    LaunchedEffect(HelperRuntime.connected, awaitingPermission, model.page) {
        if (awaitingPermission && HelperRuntime.connected && model.page == HelperEntryPage.PERMISSION) {
            awaitingPermission = false
            continueSetup()
        }
    }
    LaunchedEffect(model.page) {
        if (model.page == HelperEntryPage.HOME) preferences.edit().putBoolean("finished", true).apply()
    }
    BackHandler(model.page != HelperEntryPage.HOME && model.page != HelperEntryPage.WELCOME) {
        model = model.copy(page = HelperEntryPage.HOME)
    }
    HelperEntryScreen(
        model = model,
        onModelChange = { model = it },
        visible = HelperRuntime.visible,
        connected = HelperRuntime.connected,
        permissionError = permissionError,
        onEnable = ::continueSetup,
        onPermission = {
            permissionError = false
            awaitingPermission = true
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (
                _: android.content.ActivityNotFoundException,
            ) {
                permissionError = true
                awaitingPermission = false
            }
        },
        onConsent = {
            if (!setupBusy) {
                setupBusy = true
                setupError = false
                scope.launch {
                    try {
                        settings.acceptPrivacyNotice()
                        settings.setEnabled(true)
                    } catch (
                        cancelled: CancellationException,
                    ) {
                        throw cancelled
                    } catch (_: Exception) {
                        setupError = true
                    } finally {
                        setupBusy = false
                    }
                    if (!setupError) continueSetup()
                }
            }
        },
        onMicrophone = { microphone.launch(Manifest.permission.RECORD_AUDIO) },
        onSkipMicrophone = {
            preferences.edit().putBoolean("microphone_choice_done", true).apply()
            continueSetup()
        },
        setupBusy = setupBusy,
        setupError = setupError,
        onAsk = {
            scope.launch {
                val config = settings.getConfig()
                if (config.privacyNoticeAccepted && config.enabled) HelperRuntime.ask?.invoke() else continueSetup()
            }
        },
        onHide = { HelperRuntime.hide?.invoke() },
        onOpenPlayground = onOpenPlayground,
    )
}
