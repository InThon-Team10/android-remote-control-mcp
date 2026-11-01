@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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

private val NARROW_WIDTH = 320.dp
private val MEDIUM_WIDTH = 400.dp
private val NARROW_TITLE = 24.sp
private val MEDIUM_TITLE = 28.sp
private val WIDE_TITLE = 32.sp
private const val TITLE_LINE_SCALE = 1.3f
private val STATUS_SPACING = 12.dp

/** The live on/off state of 콕콕이: art, title and, when there is one, a body line. */
@Composable
internal fun HelperHomeStatus(
    status: HomeStatus,
    aiConnected: Boolean,
) {
    val visual = homeStatusVisual(status, aiConnected)
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STATUS_SPACING),
    ) {
        HelperMascotStateVisual(visual.awake)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val size =
                when {
                    maxWidth < NARROW_WIDTH -> NARROW_TITLE
                    maxWidth < MEDIUM_WIDTH -> MEDIUM_TITLE
                    else -> WIDE_TITLE
                }
            Column(
                Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(STATUS_SPACING),
            ) {
                Text(
                    stringResource(visual.titleRes),
                    Modifier.fillMaxWidth().semantics { heading() },
                    textAlign = TextAlign.Center,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontSize = size,
                            lineHeight = size * TITLE_LINE_SCALE,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                visual.bodyRes?.let { EntryText(it) }
            }
        }
    }
}

@Composable
internal fun HomeBody(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
) {
    HelperHomeStatus(state.homeStatus, state.setup.aiConnected)
    state.errorRes?.let { EntryText(it) }
    if (!state.accessibilityReady) EntryText(R.string.entry_accessibility_lost)
    when (state.homeAction) {
        HomeAction.OPEN -> EntryAction(R.string.helper_onboard_ask, actions::openHelper)
        HomeAction.START -> EntryAction(R.string.helper_onboard_enable_home, actions::continueSetup)
        HomeAction.RETRY -> EntryAction(R.string.entry_retry, actions::continueSetup)
        HomeAction.STARTING -> EntryAction(R.string.entry_starting, {}, enabled = false)
    }
    if (state.config.enabled) EntryLink(R.string.entry_stop, actions::turnOff)
    HorizontalDivider()
    EntryLink(R.string.helper_onboard_practice_again, actions::startPractice)
}
