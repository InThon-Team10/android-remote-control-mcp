@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ENTRY_MASCOT_SIZE = 156.dp
private val ENTRY_MASCOT_PADDING = 12.dp
private val ENTRY_MIN_TARGET = 56.dp
private val ENTRY_ACTION_PADDING = 16.dp
private val ENTRY_CARD_CORNER = 24.dp
private val ENTRY_CARD_PADDING = 20.dp
private val ENTRY_CARD_SPACING = 16.dp
private const val SMALL_SCREEN_WIDTH_DP = 360
private val TITLE_SIZE = 32.sp
private val TITLE_LINE = 42.sp
private val TITLE_SMALL_SIZE = 24.sp
private val TITLE_SMALL_LINE = 34.sp
private val BODY_SIZE = 18.sp
private val BODY_LINE = 28.sp
private val ACTION_LINE = 26.sp

@Composable
internal fun EntryMascot() {
    HelperCharacter(ENTRY_MASCOT_SIZE, Modifier.padding(vertical = ENTRY_MASCOT_PADDING))
}

/** A heading; [compact] makes it a secondary title, and narrow screens always use the small size. */
@Composable
internal fun EntryTitle(
    @StringRes text: Int,
    compact: Boolean = false,
) {
    val small = compact || LocalConfiguration.current.screenWidthDp < SMALL_SCREEN_WIDTH_DP
    Text(
        stringResource(text),
        Modifier.fillMaxWidth().semantics { heading() },
        style =
            MaterialTheme.typography.headlineMedium.copy(
                fontSize = if (small) TITLE_SMALL_SIZE else TITLE_SIZE,
                lineHeight = if (small) TITLE_SMALL_LINE else TITLE_LINE,
                fontWeight = FontWeight.Bold,
            ),
    )
}

@Composable
internal fun EntryText(
    @StringRes text: Int,
) {
    Text(
        stringResource(text),
        Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = BODY_SIZE, lineHeight = BODY_LINE),
    )
}

@Composable
internal fun EntryAction(
    @StringRes text: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = ENTRY_MIN_TARGET),
        contentPadding = PaddingValues(ENTRY_ACTION_PADDING),
    ) {
        Text(
            stringResource(text),
            textAlign = TextAlign.Center,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = BODY_SIZE,
                    lineHeight = ACTION_LINE,
                    lineBreak = LineBreak.Heading,
                ),
        )
    }
}

@Composable
internal fun EntryLink(
    @StringRes text: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    TextButton(
        onClick,
        modifier.heightIn(min = ENTRY_MIN_TARGET),
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
    ) {
        Text(stringResource(text), textAlign = TextAlign.Center, fontSize = BODY_SIZE, lineHeight = ACTION_LINE)
    }
}

@Composable
internal fun EntryCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(ENTRY_CARD_CORNER)) {
        Column(
            Modifier.fillMaxWidth().padding(ENTRY_CARD_PADDING),
            verticalArrangement = Arrangement.spacedBy(ENTRY_CARD_SPACING),
            content = content,
        )
    }
}

@Composable
internal fun EntrySwitch(
    @StringRes label: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val name = stringResource(label)
    Row(Modifier.fillMaxWidth().heightIn(min = ENTRY_MIN_TARGET), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f).clearAndSetSemantics {}, fontSize = BODY_SIZE, lineHeight = ACTION_LINE)
        Switch(checked, onChange, Modifier.semantics { contentDescription = name })
    }
}
