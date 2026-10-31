@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperCharacter

/**
 * The face of the 도움 버튼: 콕콕이 on a light plate above [label]. Stateless and gesture-free: the floating window
 * passes its drag handling and move actions through [modifier], the practice screen passes nothing. The accessible
 * name is the visible [label]; the click action is announced as "도우미 열기".
 */
@Composable
internal fun HelpButtonFace(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.bubble_open)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 12.dp, bottomEnd = 12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 6.dp,
        modifier =
            modifier
                .padding(6.dp)
                .widthIn(min = 64.dp)
                .heightIn(min = 80.dp)
                .semantics { onClick(label = openLabel, action = null) },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            HelperCharacter(size = 44.dp, plate = true)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
