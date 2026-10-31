@file:Suppress("FunctionNaming", "MagicNumber")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R

/** Visibility comes from the real overlay host; permission alone never means "on". */
@Composable
internal fun HelperHomeStatus(visible: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HelperMascotStateVisual(visible)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Size follows available width, never cancels the user's font enlargement.
            val titleSize =
                when {
                    maxWidth < 320.dp -> 24.sp
                    maxWidth < 400.dp -> 28.sp
                    else -> 32.sp
                }
            Text(
                text = stringResource(if (visible) R.string.helper_home_active_title else R.string.helper_home_inactive_title),
                fontSize = titleSize,
                lineHeight = titleSize * 1.3f,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}
