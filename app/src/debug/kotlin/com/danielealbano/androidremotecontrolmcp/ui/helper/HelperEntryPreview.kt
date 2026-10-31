@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val EntrySaver =
    listSaver<HelperEntryModel, String>(
        save = { listOf(it.page.name, it.step.name) },
        restore = { HelperEntryModel(HelperEntryPage.valueOf(it[0]), HelperPracticeStep.valueOf(it[1])) },
    )

@Composable
fun HelperEntryPreview(onOpenPlayground: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("helper_onboarding", Context.MODE_PRIVATE) }
    var model by rememberSaveable(stateSaver = EntrySaver) {
        mutableStateOf(
            HelperEntryModel(
                page =
                    if (preferences.getBoolean("finished", false)) {
                        HelperEntryPage.HOME
                    } else {
                        HelperEntryPage.WELCOME
                    },
            ),
        )
    }
    var permissionError by rememberSaveable { mutableStateOf(false) }
    var awaitingPermission by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, model.page) {
        fun refresh() {
            HelperRuntime.suppress(model.page != HelperEntryPage.HOME && model.page != HelperEntryPage.PERMISSION)
            if (awaitingPermission) {
                awaitingPermission = false
                if (HelperRuntime.connected) model = model.copy(page = HelperEntryPage.HOME)
            }
        }
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refresh()
                if (event == Lifecycle.Event.ON_PAUSE) HelperRuntime.suppress(false)
            }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
        onDispose {
            lifecycle.removeObserver(observer)
            HelperRuntime.suppress(false)
        }
    }
    LaunchedEffect(model.page) {
        if (model.page == HelperEntryPage.HOME) preferences.edit().putBoolean("finished", true).apply()
    }
    BackHandler(model.page != HelperEntryPage.HOME && model.page != HelperEntryPage.WELCOME) {
        model = model.copy(page = if (model.page == HelperEntryPage.PERMISSION) HelperEntryPage.INVITE else HelperEntryPage.WELCOME)
    }
    HelperEntryScreen(
        model = model,
        onModelChange = { model = it },
        visible = HelperRuntime.visible,
        connected = HelperRuntime.connected,
        permissionError = permissionError,
        onEnable = {
            if (HelperRuntime.connected) {
                HelperRuntime.suppress(false)
                HelperRuntime.show?.invoke()
                model = model.copy(page = HelperEntryPage.HOME)
            } else {
                model = model.copy(page = HelperEntryPage.PERMISSION)
            }
        },
        onPermission = {
            permissionError = false
            awaitingPermission = true
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: android.content.ActivityNotFoundException) {
                permissionError = true
                awaitingPermission = false
            }
        },
        onAsk = { HelperRuntime.ask?.invoke() },
        onHide = { HelperRuntime.hide?.invoke() },
        onSettings = { context.startActivity(Intent(context, HelperConnectionSettingsActivity::class.java)) },
        onOpenPlayground = onOpenPlayground,
    )
}
