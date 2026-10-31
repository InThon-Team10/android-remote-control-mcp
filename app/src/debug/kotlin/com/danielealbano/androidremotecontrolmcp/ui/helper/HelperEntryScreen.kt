@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme

@Composable
fun HelperEntryScreen(
    model: HelperEntryModel,
    onModelChange: (HelperEntryModel) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onOpenPlayground: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding()) {
            EntryHeader(model, onModelChange)
            key(model.page) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    EntryNotice()
                    EntryContent(model, onModelChange, onOpenPlayground)
                    if (model.page == HelperEntryPage.REQUEST) RequestBody(draft, onDraftChange)
                }
            }
            EntryFooter(model, onModelChange)
        }
    }
}

@Composable
private fun EntryHeader(
    model: HelperEntryModel,
    change: (HelperEntryModel) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (model.page != HelperEntryPage.WELCOME && model.page != HelperEntryPage.HOME) {
            EntryLink(R.string.entry_back) { change(model.back()) }
        }
        Text(
            stringResource(R.string.entry_name),
            Modifier.weight(1f).padding(8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        if (model.page == HelperEntryPage.HOME) {
            EntryLink(R.string.entry_settings) { change(model.copy(page = HelperEntryPage.SETTINGS)) }
        }
    }
}

@Composable
private fun EntryNotice() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            EntryText(R.string.entry_notice)
            Text(stringResource(R.string.entry_notice_detail), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EntryContent(
    model: HelperEntryModel,
    change: (HelperEntryModel) -> Unit,
    onOpenPlayground: () -> Unit,
) {
    when (model.page) {
        HelperEntryPage.WELCOME -> {
            WelcomeBody { change(model.help()) }
        }

        HelperEntryPage.SETUP -> {
            SetupBody(model.permission) { change(model.copy(page = HelperEntryPage.HOME)) }
        }

        HelperEntryPage.PERMISSION -> {
            PermissionBody { change(model.permissionResult(false)) }
        }

        HelperEntryPage.HOME -> {
            HomeBody(model, change)
        }

        HelperEntryPage.HELP -> {
            EntryTitle(R.string.entry_how_title)
            UsageExample()
            HowToUse()
            EntryText(R.string.entry_help_return)
        }

        HelperEntryPage.SETTINGS -> {
            ReviewBody(model, change, onOpenPlayground)
        }

        HelperEntryPage.REQUEST -> {
            EntryTitle(R.string.entry_request_title)
        }
    }
}

@Composable
private fun WelcomeBody(help: () -> Unit) {
    EntryTitle(R.string.entry_welcome_title)
    EntryText(R.string.entry_welcome_body)
    UsageExample()
    EntryText(R.string.entry_bubble_explanation)
    EntryLink(R.string.entry_help, help)
}

@Composable
private fun UsageExample() {
    EntryCard {
        EntryText(R.string.entry_example)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text(
                stringResource(R.string.entry_search),
                Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) { EntryText(R.string.entry_caption) }
        }
        Surface(
            modifier = Modifier.align(Alignment.End),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(
                stringResource(R.string.entry_name),
                Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SetupBody(
    ready: Boolean,
    later: () -> Unit,
) {
    EntryTitle(if (ready) R.string.entry_ready_title else R.string.entry_setup_title)
    EntryText(if (ready) R.string.entry_ready_body else R.string.entry_setup_body)
    if (!ready) {
        EntryCard {
            EntryText(R.string.entry_setup_steps_title)
            EntryText(R.string.entry_setup_steps)
        }
        EntryText(R.string.entry_permission_scope)
    }
    EntryLink(R.string.entry_later, later)
}

@Composable
private fun PermissionBody(deny: () -> Unit) {
    EntryTitle(R.string.entry_permission_title)
    EntryText(R.string.entry_permission_body)
    EntryLink(R.string.entry_deny, deny)
}

@Composable
private fun HomeBody(
    model: HelperEntryModel,
    change: (HelperEntryModel) -> Unit,
) {
    EntryTitle(R.string.entry_home_title)
    EntryCard {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(stringResource(statusTitle(model)), style = MaterialTheme.typography.titleLarge)
            EntryText(statusBody(model))
        }
        if (model.bubble == HelperEntryBubble.ON) {
            EntryLink(R.string.entry_stop) { change(model.stop()) }
        }
    }
    EntryText(R.string.entry_how_title)
    HowToUse()
    EntryLink(R.string.entry_help) { change(model.help()) }
}

@Composable
private fun HowToUse() {
    listOf(R.string.entry_how_1, R.string.entry_how_2, R.string.entry_how_3).forEachIndexed { index, text ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text("${index + 1}.", style = MaterialTheme.typography.titleLarge)
            EntryText(text)
        }
    }
}

@Composable
private fun ReviewBody(
    model: HelperEntryModel,
    change: (HelperEntryModel) -> Unit,
    onOpenPlayground: () -> Unit,
) {
    EntryTitle(R.string.entry_review_title)
    EntryText(R.string.entry_review_body)
    ReviewSwitch(R.string.entry_permission_toggle, model.permission) {
        change(if (it) model.copy(permission = true) else model.revoke())
    }
    ReviewSwitch(R.string.entry_failure_toggle, model.failNextStart) {
        change(model.copy(failNextStart = it, bubble = HelperEntryBubble.OFF))
    }
    EntryLink(R.string.entry_reset) { change(HelperEntryModel()) }
    EntryLink(R.string.entry_legacy, onOpenPlayground)
}

@Composable
private fun ReviewSwitch(
    @StringRes label: Int,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    val name = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f).clearAndSetSemantics {}, fontSize = 18.sp, lineHeight = 26.sp)
        Switch(checked, change, Modifier.semantics { contentDescription = name })
    }
}

@Composable
private fun RequestBody(
    draft: String,
    change: (String) -> Unit,
) {
    var submitted by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = draft,
        onValueChange = {
            change(it)
            submitted = false
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.entry_request_label)) },
        placeholder = { Text(stringResource(R.string.entry_request_hint)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        minLines = 3,
    )
    EntryText(R.string.entry_request_unavailable)
    Button(onClick = { submitted = true }, enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        EntryText(R.string.entry_submit)
    }
    if (submitted) {
        Text(
            stringResource(R.string.entry_request_unavailable),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            fontSize = 18.sp,
            lineHeight = 26.sp,
        )
    }
}

@Composable
private fun EntryFooter(
    model: HelperEntryModel,
    change: (HelperEntryModel) -> Unit,
) {
    Button(
        onClick = {
            change(
                when (model.page) {
                    HelperEntryPage.PERMISSION -> model.permissionResult(true)
                    HelperEntryPage.HELP, HelperEntryPage.SETTINGS, HelperEntryPage.REQUEST -> model.back()
                    else -> model.primary()
                },
            )
        },
        enabled = model.bubble != HelperEntryBubble.STARTING,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 56.dp),
    ) {
        EntryText(primaryLabel(model))
    }
}

@StringRes
private fun primaryLabel(model: HelperEntryModel): Int =
    when (model.page) {
        HelperEntryPage.WELCOME -> R.string.entry_setup_start
        HelperEntryPage.PERMISSION -> R.string.entry_grant
        HelperEntryPage.HELP -> R.string.entry_return
        HelperEntryPage.SETTINGS -> R.string.entry_home
        HelperEntryPage.REQUEST -> R.string.entry_return_bubble
        HelperEntryPage.SETUP -> if (model.permission) R.string.entry_start else R.string.entry_open_settings
        HelperEntryPage.HOME -> homeAction(model)
    }

@StringRes
private fun homeAction(model: HelperEntryModel): Int =
    when {
        !model.permission -> R.string.entry_continue_setup
        model.bubble == HelperEntryBubble.ON -> R.string.entry_open
        model.bubble == HelperEntryBubble.STARTING -> R.string.entry_starting
        model.bubble == HelperEntryBubble.FAILED -> R.string.entry_retry
        else -> R.string.entry_start
    }

@StringRes
private fun statusTitle(model: HelperEntryModel): Int =
    when {
        !model.permission -> R.string.entry_status_missing
        model.bubble == HelperEntryBubble.ON -> R.string.entry_status_on
        model.bubble == HelperEntryBubble.STARTING -> R.string.entry_status_starting
        model.bubble == HelperEntryBubble.FAILED -> R.string.entry_status_failed
        else -> R.string.entry_status_off
    }

@StringRes
private fun statusBody(model: HelperEntryModel): Int =
    when {
        !model.permission -> R.string.entry_body_missing
        model.bubble == HelperEntryBubble.ON -> R.string.entry_body_on
        model.bubble == HelperEntryBubble.STARTING -> R.string.entry_body_starting
        model.bubble == HelperEntryBubble.FAILED -> R.string.entry_body_failed
        else -> R.string.entry_body_off
    }

@Composable
private fun EntryTitle(
    @StringRes text: Int,
) {
    Text(stringResource(text), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun EntryText(
    @StringRes text: Int,
) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp))
}

@Composable
private fun EntryLink(
    @StringRes text: Int,
    onClick: () -> Unit,
) {
    TextButton(
        onClick,
        Modifier.heightIn(min = 56.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
    ) { EntryText(text) }
}

@Composable
private fun EntryCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Preview(name = "첫 실행", locale = "ko", widthDp = 360, heightDp = 800)
@Preview(name = "큰 글씨", locale = "ko", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
private fun EntryWelcomePreview() {
    AndroidRemoteControlMcpTheme(dynamicColor = false) {
        HelperEntryScreen(HelperEntryModel(), {}, "", {}, {})
    }
}
