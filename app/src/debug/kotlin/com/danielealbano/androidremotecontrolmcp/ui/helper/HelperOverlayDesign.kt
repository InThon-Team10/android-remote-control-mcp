@file:Suppress("FunctionNaming", "LongMethod", "LongParameterList", "MagicNumber", "UnusedPrivateFunction")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.theme.AndroidRemoteControlMcpTheme
import com.danielealbano.androidremotecontrolmcp.ui.theme.LightColorScheme

@Composable
internal fun HelperEdgeCharacter(onExpand: () -> Unit) {
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    Surface(
        onClick = onExpand,
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 12.dp, bottomEnd = 12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
        modifier =
            Modifier
                .padding(6.dp)
                .size(width = 64.dp, height = 80.dp)
                .semantics { role = Role.Button }
                .pointerInput(onExpand) {
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { distance = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            distance += amount
                        },
                        onDragEnd = { if (distance < -threshold) onExpand() },
                        onDragCancel = { distance = 0f },
                    )
                },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CharacterFace()
            Text(stringResource(R.string.helper_overlay_help), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun CharacterFace() {
    Image(painterResource(R.drawable.helper_phone_mascot), contentDescription = null, modifier = Modifier.size(44.dp))
}

@Composable
internal fun HelperOverlaySpeech(
    request: String,
    onRequestChange: (String) -> Unit,
    onCollapse: () -> Unit,
    maxHeightDp: Int,
    guiding: Boolean = false,
    onTest: () -> Unit = {},
    onSubmit: () -> Unit = {},
    busy: Boolean = false,
    message: String? = null,
    isListening: Boolean = false,
    isRecognizing: Boolean = false,
    voiceStatus: String? = null,
    onVoiceToggle: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val voiceLabel =
        stringResource(
            when {
                isListening -> R.string.helper_overlay_voice_stop
                isRecognizing -> R.string.helper_overlay_voice_processing
                else -> R.string.helper_overlay_speak
            },
        )
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).heightIn(max = maxHeightDp.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A fixed return action stays separate from the scrolling input controls.
        Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.CenterEnd) {
            FilledIconButton(
                onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    onCollapse()
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(Icons.Rounded.Close, stringResource(R.string.helper_overlay_collapse))
            }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (guiding) {
                Text(
                    stringResource(R.string.helper_overlay_search_instruction),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = request,
                    enabled = !busy,
                    readOnly = isRecognizing || isListening,
                    onValueChange = onRequestChange,
                    placeholder = { Text(stringResource(R.string.helper_overlay_input_hint)) },
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    singleLine = false,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = LightColorScheme.surface,
                            unfocusedContainerColor = LightColorScheme.surface,
                            disabledContainerColor = LightColorScheme.surface,
                            focusedTextColor = LightColorScheme.onSurface,
                            unfocusedTextColor = LightColorScheme.onSurface,
                            disabledTextColor = LightColorScheme.onSurface,
                            focusedPlaceholderColor = LightColorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = LightColorScheme.onSurfaceVariant,
                            disabledPlaceholderColor = LightColorScheme.onSurfaceVariant,
                            cursorColor = LightColorScheme.primary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).fillMaxHeight(),
                )
                FilledIconButton(
                    onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        onVoiceToggle()
                    },
                    enabled = !busy && (!isRecognizing || isListening),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(64.dp).heightIn(min = 64.dp).fillMaxHeight(),
                ) {
                    Icon(
                        if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = voiceLabel,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            voiceStatus?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        Button(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                if (isConsequenceTestRequest(request)) onTest() else onSubmit()
            },
            enabled = request.isNotBlank() && !busy && !isRecognizing && !isListening,
            colors =
                ButtonDefaults.buttonColors(
                    disabledContainerColor = LightColorScheme.surfaceVariant,
                    disabledContentColor = LightColorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(if (busy) R.string.helper_overlay_preparing else R.string.helper_overlay_submit), fontSize = 18.sp)
        }
    }
}

/** Non-touchable dim layer separates the bright input from the current app. */
@Composable
internal fun HelperOverlayFrame(
    guiding: Boolean = false,
    targetBounds: Rect? = null,
    instruction: String = stringResource(R.string.helper_overlay_search_instruction),
    captionVisible: Boolean = true,
    highlightVisible: Boolean = guiding,
    onCaptionFit: (Boolean) -> Unit = {},
    captionTopInset: Int = 0,
    captionBottomInset: Int = 0,
) {
    val blue = Color(0xFF819DFF)
    val violet = Color(0xFFB3A7F2)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val target =
            targetBounds ?: with(density) {
                val center = Offset(maxWidth.toPx() * 0.51f, maxHeight.toPx() * 0.095f)
                val radius = 32.dp.toPx()
                Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
            }
        Canvas(Modifier.fillMaxSize()) {
            if (highlightVisible) {
                val hole =
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                target,
                                CornerRadius(10.dp.toPx()),
                            ),
                        )
                    }
                clipPath(hole, clipOp = ClipOp.Difference) {
                    drawRect(Color.Black.copy(alpha = 0.72f))
                }
                drawRoundRect(
                    Color.White,
                    topLeft = target.topLeft,
                    size = target.size,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.5.dp.toPx()),
                )
            } else {
                drawRect(Color.Black.copy(alpha = 0.78f))
            }
            val depth = 22.dp.toPx().coerceAtMost(size.minDimension * 0.15f)

            // Bright at the physical edge, continuously transparent toward the app.
            fun edgeBrush(
                color: Color,
                start: Offset,
                end: Offset,
            ) = Brush.linearGradient(
                0f to color.copy(alpha = 0.95f),
                0.08f to color.copy(alpha = 0.85f),
                0.25f to color.copy(alpha = 0.50f),
                0.55f to color.copy(alpha = 0.15f),
                1f to color.copy(alpha = 0f),
                start = start,
                end = end,
            )
            drawRect(edgeBrush(violet, Offset.Zero, Offset(depth, 0f)))
            drawRect(edgeBrush(blue, Offset(size.width, 0f), Offset(size.width - depth, 0f)))
            drawRect(edgeBrush(violet, Offset.Zero, Offset(0f, depth)))
            drawRect(edgeBrush(blue, Offset(0f, size.height), Offset(0f, size.height - depth)))
        }
        if (guiding) {
            HelperAnchoredCaption(target, instruction, captionVisible, onCaptionFit, captionTopInset, captionBottomInset)
        }
    }
}

/** Measure the actual, font-scaled caption before choosing its side. Coordinates are overlay-local px. */
@Composable
private fun HelperAnchoredCaption(
    target: Rect,
    instruction: String,
    visible: Boolean,
    onFit: (Boolean) -> Unit,
    topInset: Int,
    bottomInset: Int,
) {
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val margin = 16.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val tailHeight = 8.dp.roundToPx()
        val body =
            subcompose("body") {
                Surface(
                    color = LightColorScheme.surface,
                    contentColor = LightColorScheme.onSurface,
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 0.dp,
                ) {
                    Text(
                        instruction,
                        style = MaterialTheme.typography.titleLarge,
                        modifier =
                            Modifier
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }.single().measure(
                Constraints(maxWidth = minOf(320.dp.roundToPx(), (constraints.maxWidth - margin * 2).coerceAtLeast(0))),
            )
        val placement =
            placeHelperCaption(
                constraints.maxWidth,
                (constraints.maxHeight - topInset - bottomInset).coerceAtLeast(0),
                target.left.toInt(),
                target.top.toInt() - topInset,
                target.right.toInt(),
                target.bottom.toInt() - topInset,
                body.width,
                body.height,
                margin,
                gap + tailHeight,
            )
        onFit(placement.fits)
        val tip = ((target.center.x - placement.left).coerceIn(0f, body.width.toFloat()))
        val base = tip.coerceIn(minOf(32.dp.toPx(), body.width / 2f), maxOf(body.width - 32.dp.toPx(), body.width / 2f))
        val tail =
            subcompose("tail") {
                Canvas(Modifier.fillMaxSize()) {
                    val path =
                        Path().apply {
                            val baseY = if (placement.below) size.height else 0f
                            val tipY = if (placement.below) 0f else size.height
                            moveTo(base - 8.dp.toPx(), baseY)
                            lineTo(tip, tipY)
                            lineTo(base + 8.dp.toPx(), baseY)
                            close()
                        }
                    drawPath(path, LightColorScheme.surface)
                }
            }.single().measure(Constraints.fixed(body.width, tailHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (placement.fits && visible) body.place(placement.left, placement.top + topInset)
            if (placement.fits &&
                visible
            ) {
                tail.place(placement.left, topInset + if (placement.below) placement.top - tailHeight else placement.top + body.height)
            }
        }
    }
}

@Composable
internal fun HelperGuideControls(
    menuExpanded: Boolean,
    guideHidden: Boolean,
    onToggleMenu: () -> Unit,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onRetry: () -> Unit,
    onCollapse: () -> Unit,
    onExit: () -> Unit,
    onSpeak: () -> Unit,
    speechReady: Boolean,
    maxHeightDp: Int,
    fallback: Boolean = false,
) {
    if (!menuExpanded && !fallback) {
        HelperControlActions(
            onReturn = onCollapse,
            secondaryLabel = if (guideHidden) R.string.helper_overlay_resume else R.string.helper_overlay_menu,
            onSecondary = if (guideHidden) onHide else onToggleMenu,
        )
        return
    }
    Surface(
        color = LightColorScheme.surface,
        contentColor = LightColorScheme.onSurface,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = maxHeightDp.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            HelperControlActions(
                onReturn = onCollapse,
                secondaryLabel = if (menuExpanded) R.string.helper_overlay_menu_close else R.string.helper_overlay_menu,
                onSecondary = onToggleMenu,
            )
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(if (menuExpanded) R.string.helper_overlay_menu else R.string.helper_overlay_search_instruction),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(12.dp),
                )
                if (menuExpanded) {
                    HelperMenuAction(R.string.helper_overlay_retry, Icons.Rounded.ChatBubbleOutline, onRetry)
                    HelperMenuAction(
                        if (speechReady) R.string.helper_overlay_read else R.string.helper_overlay_read_unavailable,
                        Icons.Rounded.VolumeUp,
                        onSpeak,
                        enabled = speechReady,
                    )
                    HelperMenuAction(R.string.helper_overlay_hide_guide, Icons.Rounded.VisibilityOff, onHide)
                    HelperMenuAction(R.string.helper_overlay_move_menu, Icons.Rounded.OpenWith, onMove)
                    HelperMenuAction(R.string.helper_overlay_stop, Icons.Rounded.PowerSettingsNew, onExit)
                }
            }
        }
    }
}

@Composable
internal fun HelperReturnToBubbleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HelperOverlayTextButton(R.string.helper_overlay_collapse, onClick, modifier)
}

@Composable
private fun HelperOverlayTextButton(
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = LightColorScheme.surface,
        contentColor = LightColorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = modifier.heightIn(min = 56.dp).semantics { role = Role.Button },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Measure the real translated/font-scaled labels; stack when one row would truncate them. */
@Composable
internal fun HelperControlActions(
    onReturn: () -> Unit,
    secondaryLabel: Int,
    onSecondary: () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val gap = 8.dp.roundToPx()
        val loose = Constraints(maxWidth = constraints.maxWidth)
        val secondary = subcompose("secondary") { HelperOverlayTextButton(secondaryLabel, onSecondary) }.single().measure(loose)
        val back = subcompose("return") { HelperReturnToBubbleButton(onReturn) }.single().measure(loose)
        val row = secondary.width + gap + back.width <= constraints.maxWidth
        val width = if (row) secondary.width + gap + back.width else maxOf(secondary.width, back.width)
        val height = if (row) maxOf(secondary.height, back.height) else secondary.height + gap + back.height
        layout(width, height) {
            secondary.place(0, 0)
            back.place(if (row) secondary.width + gap else 0, if (row) 0 else secondary.height + gap)
        }
    }
}

@Composable
internal fun HelperMenuAction(
    label: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = LightColorScheme.onSurface),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
    }
}

@Preview(name = "Transparent overlay input", widthDp = 380, showBackground = true, locale = "ko")
@Composable
private fun OverlaySpeechPreview() {
    AndroidRemoteControlMcpTheme(dynamicColor = false) {
        HelperOverlaySpeech("", {}, {}, maxHeightDp = 600)
    }
}

@Preview(name = "Edge character", showBackground = true, locale = "ko")
@Composable
private fun EdgeCharacterPreview() {
    AndroidRemoteControlMcpTheme(dynamicColor = false) { HelperEdgeCharacter {} }
}
