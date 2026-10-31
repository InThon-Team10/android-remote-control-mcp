@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.debug.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.assistant.session.ChatItem
import com.danielealbano.androidremotecontrolmcp.assistant.session.NoticeKind
import com.danielealbano.androidremotecontrolmcp.assistant.voice.LiveVoiceState
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig

private val MinTarget = Modifier.heightIn(min = 48.dp)

@Composable
fun AssistantTestScreen(
    viewModel: AssistantTestViewModel,
    onSend: (String) -> Unit,
    onTalk: () -> Unit,
) {
    val config by viewModel.config.collectAsState()
    val items by viewModel.session.items.collectAsState()
    val liveState by viewModel.liveState.collectAsState()
    val message by viewModel.message.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSection(config, message, viewModel)
        LazyColumn(Modifier.weight(1f)) {
            items(items, key = { it.id }) { item -> TranscriptRow(item, viewModel) }
        }
        InputSection(liveState, viewModel, onSend, onTalk)
    }
}

@Composable
private fun SettingsSection(
    config: AssistantConfig,
    message: String?,
    viewModel: AssistantTestViewModel,
) {
    var key by rememberSaveable { mutableStateOf("") }
    var accepted by rememberSaveable { mutableStateOf(false) }
    var stepLimit by remember(config.stepLimit) { mutableStateOf(config.stepLimit.toString()) }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(if (config.apiKeyConfigured) "API key (saved)" else "API key") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { viewModel.saveApiKey(key).also { key = "" } }, modifier = MinTarget) {
        Text("Save / clear key (blank clears)")
    }
    LabeledRow("Enabled", config.enabled) { viewModel.setEnabled(it, accepted || config.privacyNoticeAccepted) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = accepted || config.privacyNoticeAccepted, onCheckedChange = { accepted = it })
        Text("I accept the privacy notice")
    }
    LabeledRow("Voice", config.voiceEnabled, viewModel::setVoiceEnabled)
    AssistantConfig.MODELS.forEach { model ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = config.model == model, onClick = { viewModel.setModel(model) })
            Text(model)
        }
    }
    OutlinedTextField(
        value = stepLimit,
        onValueChange = {
            stepLimit = it
            viewModel.setStepLimit(it)
        },
        label = { Text("Step limit") },
        modifier = Modifier.fillMaxWidth(),
    )
    message?.let { Text(it) }
}

@Composable
private fun LabeledRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(MinTarget, Arrangement.spacedBy(8.dp), Alignment.CenterVertically) {
        Text(label)
        Switch(checked, onChange, Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun TranscriptRow(
    item: ChatItem,
    viewModel: AssistantTestViewModel,
) {
    Text(item.rowText())
    if (item is ChatItem.Confirmation && item.answered == null) {
        Row {
            TextButton(onClick = { viewModel.session.answerConfirmation(item.callId, true) }, modifier = MinTarget) {
                Text("Allow")
            }
            TextButton(onClick = { viewModel.session.answerConfirmation(item.callId, false) }, modifier = MinTarget) {
                Text("Deny")
            }
        }
    }
}

@Composable
private fun InputSection(
    liveState: LiveVoiceState,
    viewModel: AssistantTestViewModel,
    onSend: (String) -> Unit,
    onTalk: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Message") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Live voice: ${liveState.javaClass.simpleName}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onSend(text).also { text = "" } }, modifier = MinTarget) { Text("Send") }
        Button(onClick = if (liveState.isActive()) viewModel::endTalk else onTalk, modifier = MinTarget) {
            Text(if (liveState.isActive()) "End talk" else "Talk")
        }
        Button(onClick = viewModel.session::stop, modifier = MinTarget) { Text("Stop") }
        Button(onClick = viewModel.session::newChat, modifier = MinTarget) { Text("New chat") }
    }
}

internal fun ChatItem.rowText(): String =
    when (this) {
        is ChatItem.User -> "You: $text"
        is ChatItem.Assistant -> "Assistant: $text"
        is ChatItem.ToolCall -> "Tool $toolName: ${status.name}"
        is ChatItem.Confirmation -> "Confirm $toolName: ${answered.answerText()}"
        is ChatItem.Error -> "Error: ${error.javaClass.simpleName}"
        is ChatItem.Notice -> "Notice: ${kind.noticeText()}"
    }

private fun Boolean?.answerText(): String =
    when (this) {
        null -> "PENDING"
        true -> "ALLOWED"
        false -> "DENIED"
    }

private fun NoticeKind.noticeText(): String =
    when (this) {
        is NoticeKind.StepLimitReached -> "step limit $limit reached"
        NoticeKind.Stopped -> "stopped"
    }

private fun LiveVoiceState.isActive(): Boolean = this !is LiveVoiceState.Idle && this !is LiveVoiceState.Error
