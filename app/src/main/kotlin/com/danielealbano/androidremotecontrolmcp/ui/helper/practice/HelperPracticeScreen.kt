@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperEntryActions
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperEntryModel
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperEntryUiState
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperPracticeStep
import com.danielealbano.androidremotecontrolmcp.ui.helper.bubble.HelpButtonFace

private val CONTENT_MAX_WIDTH = 560.dp
private val BAR_PADDING = 16.dp
private val EXIT_MAX_WIDTH = 112.dp
private val MIN_TARGET = 56.dp
private val STEP_PADDING = 20.dp
private val STEP_SPACING = 20.dp
private val SCENERY_PADDING = 24.dp
private val SCENERY_TITLE = 30.sp
private val SCENERY_ROW = 22.sp
private val SCENERY_ROW_PADDING = 18.dp
private val SCENERY_GEAR = 36.dp
private const val SCRIM_ALPHA = 0.78f
private val OUTLINE_WIDTH = 2.dp
private val OUTLINE_CORNER = 34.dp
private val OUTLINE_PADDING = 6.dp
private val TARGET_MIN_HEIGHT = 96.dp
private val TARGET_CORNER = 16.dp
private val TARGET_BORDER = 3.dp
private val TARGET_PADDING = 16.dp
private val TARGET_TITLE = 24.sp
private val TARGET_DETAIL = 18.sp
private val DONE_MIN_HEIGHT = 64.dp
private val DONE_PADDING = 20.dp
private val DONE_TEXT = 20.sp
private val DONE_LINE = 28.sp
private val NOTICE_SIZE = 18.sp
private val NOTICE_LINE = 26.sp

/**
 * The full-screen practice: a fake Settings app, dimmed, with the real 도움 버튼 and input in front of it. Nothing
 * here grants a permission, starts a service or sends a request.
 */
@Composable
internal fun HelperPracticeScreen(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        PracticeTopBar { actions.navigate(HelperEntryModel::home) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PracticeScenery()
            key(state.model.step) { PracticeStep(state, actions) }
        }
    }
}

@Composable
private fun PracticeTopBar(onExit: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = BAR_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.helper_practice_notice),
                Modifier.weight(1f).padding(vertical = BAR_PADDING),
                fontSize = NOTICE_SIZE,
                lineHeight = NOTICE_LINE,
            )
            TextButton(onExit, Modifier.widthIn(max = EXIT_MAX_WIDTH).heightIn(min = MIN_TARGET)) {
                Text(
                    stringResource(R.string.helper_practice_exit),
                    textAlign = TextAlign.Center,
                    fontSize = NOTICE_SIZE,
                    lineHeight = NOTICE_LINE,
                )
            }
        }
    }
}

/** The pretend Settings app behind the practice: hidden from the screen reader and covered by a dark scrim. */
@Composable
private fun PracticeScenery() {
    Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(SCENERY_PADDING),
            verticalArrangement = Arrangement.spacedBy(SCENERY_ROW_PADDING),
        ) {
            Icon(
                Icons.Rounded.Settings,
                null,
                Modifier.padding(top = SCENERY_PADDING).size(SCENERY_GEAR),
                MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.helper_practice_settings),
                fontSize = SCENERY_TITLE,
                fontWeight = FontWeight.Bold,
            )
            listOf(
                R.string.helper_practice_connections,
                R.string.helper_practice_sound,
                R.string.helper_practice_notifications,
                R.string.helper_practice_display,
                R.string.helper_practice_battery,
            ).forEach { row ->
                Text(stringResource(row), fontSize = SCENERY_ROW)
                HorizontalDivider()
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = SCRIM_ALPHA)))
    }
}

@Composable
private fun PracticeStep(
    state: HelperEntryUiState,
    actions: HelperEntryActions,
) {
    val step = state.model.step
    if (step == HelperPracticeStep.ASK) {
        PracticeQuestion(state, actions)
        return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .widthIn(max = CONTENT_MAX_WIDTH)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(STEP_PADDING),
            verticalArrangement = Arrangement.spacedBy(STEP_SPACING),
        ) {
            PracticeCoach(step)
            PracticeAction(step, actions)
        }
    }
}

@Composable
private fun PracticeAction(
    step: HelperPracticeStep,
    actions: HelperEntryActions,
) {
    val advance = { actions.navigate(HelperEntryModel::advance) }
    when (step) {
        HelperPracticeStep.ASK -> Unit
        HelperPracticeStep.OPEN -> PracticeHelpButton(advance)
        HelperPracticeStep.FOLLOW -> PracticeTarget(advance)
        HelperPracticeStep.DONE -> PracticeDoneButton(advance)
    }
}

/** The real 도움 버튼 face, outlined so it stands out from the dimmed scenery; the practice's first move. */
@Composable
private fun PracticeHelpButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier
                .border(BorderStroke(OUTLINE_WIDTH, Color.White), RoundedCornerShape(OUTLINE_CORNER))
                .padding(OUTLINE_PADDING),
        ) { HelpButtonFace(stringResource(R.string.bubble_label), onClick) }
    }
}

@Composable
private fun PracticeDoneButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = DONE_MIN_HEIGHT),
        contentPadding = PaddingValues(DONE_PADDING),
    ) {
        Text(
            stringResource(R.string.helper_practice_complete),
            fontSize = DONE_TEXT,
            lineHeight = DONE_LINE,
            textAlign = TextAlign.Center,
        )
    }
}

/** The highlighted menu entry of the fake Settings app, shown at once; tapping it is the practice's last move. */
@Composable
private fun PracticeTarget(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(TARGET_CORNER),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(TARGET_BORDER, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().heightIn(min = TARGET_MIN_HEIGHT),
    ) {
        Column(Modifier.padding(TARGET_PADDING), verticalArrangement = Arrangement.Center) {
            Text(
                stringResource(R.string.helper_practice_display),
                fontSize = TARGET_TITLE,
                fontWeight = FontWeight.Bold,
            )
            Text(stringResource(R.string.helper_practice_display_detail), fontSize = TARGET_DETAIL)
        }
    }
}
