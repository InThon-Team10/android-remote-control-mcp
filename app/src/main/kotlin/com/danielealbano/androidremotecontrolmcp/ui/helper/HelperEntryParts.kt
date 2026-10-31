@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun EntryTitle(
    @StringRes text: Int,
) {
    Text(stringResource(text), Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
}

@Composable
internal fun EntryText(
    @StringRes text: Int,
) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp))
}

@Composable
internal fun EntryLink(
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
internal fun EntryCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f).clearAndSetSemantics {}, fontSize = 18.sp, lineHeight = 26.sp)
        Switch(checked, onChange, Modifier.semantics { contentDescription = name })
    }
}
