@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SentimentSatisfiedAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.assistant.session.SessionState
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme
import kotlinx.coroutines.flow.StateFlow

/** The UI team's light colours; the dark scheme follows the system. Dynamic colour is off to keep their palette. */
@Composable
internal fun BubbleTheme(content: @Composable () -> Unit) {
    AndroidRemoteControlMcpTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = false, content = content)
}

@Composable
internal fun BubbleCharacterWindow(
    state: BubbleUiState,
    actions: BubbleActions,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val busy by state.busy.collectAsState()
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    val moveUp = stringResource(R.string.bubble_move_up)
    val moveDown = stringResource(R.string.bubble_move_down)
    val step = with(LocalDensity.current) { BUBBLE_MOVE_STEP.toPx() }
    val label = stringResource(if (busy) R.string.bubble_label_busy else R.string.bubble_label)
    HelpButtonFace(
        label = label,
        onClick = actions::onToggleBubble,
        modifier =
            Modifier
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { dragEnd() }, onDragCancel = { dragEnd() }) { change, y ->
                        change.consume()
                        drag(y)
                    }
                }.semantics {
                    // TalkBack users cannot drag: the same move is offered as two actions.
                    customActions =
                        listOf(
                            CustomAccessibilityAction(moveUp) { moveBy(-step, drag, dragEnd) },
                            CustomAccessibilityAction(moveDown) { moveBy(step, drag, dragEnd) },
                        )
                },
    )
}

@Composable
internal fun BubblePanelWindow(
    state: BubbleUiState,
    actions: BubbleActions,
    focusTick: StateFlow<Int>,
) {
    val items by state.items.collectAsState()
    val session by state.session.collectAsState()
    val message = remember(items) { panelMessage(items) }
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        PanelInput(state, actions, focusTick, message, session)
    }
}

@Composable
private fun PanelInput(
    state: BubbleUiState,
    actions: BubbleActions,
    focusTick: StateFlow<Int>,
    message: PanelMessage,
    session: SessionState,
) {
    val text by state.draft.collectAsState()
    val busy by state.busy.collectAsState()
    val voiceEnabled by state.voiceEnabled.collectAsState()
    val listening by state.listening.collectAsState()
    val tick by focusTick.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val micVisual =
        when {
            !voiceEnabled -> MicVisual.HIDDEN
            listening -> MicVisual.LISTENING
            else -> MicVisual.IDLE
        }
    LaunchedEffect(tick) {
        focus.requestFocus()
        keyboard?.show()
    }
    HelperRequestInput(
        ui = RequestInputUi(text, false, micVisual, null, sendEnabled(text, busy), focus),
        actions =
            RequestInputActions(
                onTextChange = state::setDraft,
                onMic = actions::onToggleMic,
                onSend = {
                    if (sendEnabled(text, busy)) actions.onSend(text.trim())
                },
                onClose = actions::onClosePanel,
            ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        above = {
            PanelMessageView(message, session, actions)
            if (busy) RunningIndicator()
        },
        below = { PanelButtons(actions, session != SessionState.Idle) },
    )
}

@Composable
private fun PanelMessageView(
    message: PanelMessage,
    session: SessionState,
    actions: BubbleActions,
) {
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (message) {
        PanelMessage.None -> Unit
        is PanelMessage.Answer -> SpeechBubble(message.text, live)
        is PanelMessage.Problem -> SpeechBubble(stringResource(message.textRes), live)
        is PanelMessage.Confirm -> ConfirmationView(message, session, actions)
    }
}

@Composable
private fun SpeechBubble(
    text: String,
    modifier: Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ConfirmationView(
    message: PanelMessage.Confirm,
    session: SessionState,
    actions: BubbleActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SpeechBubble(
            stringResource(R.string.bubble_confirm, message.toolName),
            Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { actions.onConfirm(message.callId, true) },
                enabled = session == SessionState.AwaitingConfirmation,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.bubble_allow)) }
            OutlinedButton(
                onClick = { actions.onConfirm(message.callId, false) },
                enabled = session == SessionState.AwaitingConfirmation,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.bubble_deny)) }
        }
    }
}

@Composable
private fun RunningIndicator() {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(R.string.bubble_running), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PanelButtons(
    actions: BubbleActions,
    running: Boolean,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = actions::onStop, enabled = running, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.bubble_stop))
        }
        TextButton(onClick = actions::onNewChat, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(stringResource(R.string.bubble_new_chat))
        }
    }
}

/** Distance the bubble moves per accessibility "move" action. */
private val BUBBLE_MOVE_STEP = 96.dp

private fun moveBy(
    deltaY: Float,
    drag: (Float) -> Unit,
    dragEnd: () -> Unit,
): Boolean {
    drag(deltaY)
    dragEnd()
    return true
}
