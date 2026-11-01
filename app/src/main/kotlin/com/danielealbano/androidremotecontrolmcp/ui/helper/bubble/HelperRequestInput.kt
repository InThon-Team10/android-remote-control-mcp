@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R

/** What the microphone button of the request input shows. [HIDDEN] removes it. */
internal enum class MicVisual { HIDDEN, IDLE, LISTENING, PROCESSING, SPEAKING, MUTED }

internal data class RequestInputUi(
    val text: String,
    val readOnly: Boolean,
    val micVisual: MicVisual,
    val statusText: String?,
    val sendEnabled: Boolean,
    /** Moves keyboard focus to the field when the panel asks for it; null in the practice. */
    val focusRequester: FocusRequester? = null,
)

internal data class RequestInputActions(
    val onTextChange: (String) -> Unit,
    val onMic: () -> Unit,
    val onSend: () -> Unit,
    val onClose: () -> Unit,
)

private val BODY_STYLE = TextStyle(fontSize = 18.sp, lineHeight = 26.sp)
private val FIELD_SHAPE = RoundedCornerShape(24.dp)
private const val FIELD_MAX_LINES = 5

/**
 * The request input shared by the floating panel and the practice: a fixed close button on top, a scrollable middle
 * ([above], the field and mic, the status line, [below]) and the fixed submit button, so that with a 200% font and
 * the keyboard open the close and submit buttons stay reachable.
 */
@Composable
internal fun HelperRequestInput(
    ui: RequestInputUi,
    actions: RequestInputActions,
    modifier: Modifier = Modifier,
    above: @Composable ColumnScope.() -> Unit = {},
    below: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledIconButton(onClick = actions.onClose, modifier = Modifier.size(56.dp).align(Alignment.End)) {
            Icon(Icons.Rounded.Close, stringResource(R.string.bubble_close))
        }
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            above()
            InputRow(ui, actions)
            if (ui.statusText != null) {
                Text(
                    text = ui.statusText,
                    style = BODY_STYLE,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            below()
        }
        Button(
            onClick = actions.onSend,
            enabled = ui.sendEnabled,
            colors =
                ButtonDefaults.buttonColors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.bubble_send), style = BODY_STYLE) }
    }
}

@Composable
private fun InputRow(
    ui: RequestInputUi,
    actions: RequestInputActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fieldModifier = ui.focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
        OutlinedTextField(
            value = ui.text,
            onValueChange = actions.onTextChange,
            readOnly = ui.readOnly,
            placeholder = { Text(stringResource(R.string.bubble_input_hint), style = BODY_STYLE) },
            textStyle = BODY_STYLE,
            shape = FIELD_SHAPE,
            maxLines = FIELD_MAX_LINES,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            modifier = fieldModifier.weight(1f).heightIn(min = 64.dp).fillMaxHeight(),
        )
        if (ui.micVisual != MicVisual.HIDDEN) MicButton(ui.micVisual, actions.onMic)
    }
}

@Composable
private fun MicButton(
    visual: MicVisual,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = visual != MicVisual.PROCESSING,
        shape = FIELD_SHAPE,
        modifier = Modifier.width(64.dp).heightIn(min = 64.dp).fillMaxHeight(),
    ) {
        Icon(
            imageVector = micIcon(visual),
            contentDescription = stringResource(micLabel(visual)),
            modifier = Modifier.size(28.dp),
        )
    }
}

private fun micIcon(visual: MicVisual) =
    when (visual) {
        MicVisual.LISTENING, MicVisual.MUTED -> Icons.Rounded.MicOff
        MicVisual.SPEAKING -> Icons.Rounded.VolumeUp
        else -> Icons.Rounded.Mic
    }

@StringRes
private fun micLabel(visual: MicVisual): Int =
    when (visual) {
        MicVisual.LISTENING -> R.string.bubble_mic_stop
        MicVisual.PROCESSING -> R.string.bubble_mic_processing
        MicVisual.SPEAKING -> R.string.bubble_mic_speaking
        MicVisual.MUTED -> R.string.bubble_mic_muted
        else -> R.string.bubble_mic_start
    }
