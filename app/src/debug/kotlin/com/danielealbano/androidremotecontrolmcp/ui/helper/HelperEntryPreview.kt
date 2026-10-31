@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

private val EntrySaver =
    listSaver<HelperEntryModel, Any>(
        save = { listOf(it.page.name, it.returnPage.name, it.introSeen, it.permission, it.bubble.name, it.failNextStart) },
        restore = {
            HelperEntryModel(
                HelperEntryPage.valueOf(it[0] as String),
                HelperEntryPage.valueOf(it[1] as String),
                it[2] as Boolean,
                it[3] as Boolean,
                HelperEntryBubble.valueOf(it[4] as String),
                it[5] as Boolean,
            )
        },
    )

/** An isolated debug prototype: preferences below are not real assistant settings. */
@Composable
fun HelperEntryPreview(onOpenPlayground: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("helper_entry_mockup", Context.MODE_PRIVATE) }
    var model by rememberSaveable(stateSaver = EntrySaver) {
        val seen = preferences.getBoolean("intro_seen", false)
        mutableStateOf(
            HelperEntryModel(
                page = if (seen) HelperEntryPage.HOME else HelperEntryPage.WELCOME,
                introSeen = seen,
                permission = preferences.getBoolean("mock_permission", false),
            ),
        )
    }
    var draft by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(model.introSeen, model.permission) {
        preferences
            .edit()
            .putBoolean("intro_seen", model.introSeen)
            .putBoolean("mock_permission", model.permission)
            .apply()
    }
    LaunchedEffect(model.bubble) {
        if (model.bubble == HelperEntryBubble.STARTING) {
            delay(800)
            model = model.finishStart()
        }
    }
    BackHandler(model.page != HelperEntryPage.HOME && model.page != HelperEntryPage.WELCOME) {
        model = model.back()
    }
    HelperEntryScreen(
        model = model,
        onModelChange = { model = it },
        draft = draft,
        onDraftChange = { draft = it },
        onOpenPlayground = onOpenPlayground,
    )
}
