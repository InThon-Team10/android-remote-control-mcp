@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperCharacter
import com.danielealbano.androidremotecontrolmcp.ui.helper.HelperPracticeStep
import com.danielealbano.androidremotecontrolmcp.ui.helper.practiceCoach

private val COACH_CHARACTER = 72.dp
private val COACH_SPACING = 12.dp
private val COACH_CORNER = 20.dp
private val COACH_PADDING = 24.dp
private val COACH_TITLE = 24.sp
private val COACH_TITLE_LINE = 32.sp
private val COACH_BODY = 18.sp
private val COACH_BODY_LINE = 28.sp

/** 콕콕이's speech for a practice [step]: progress line and a card whose text is announced when it changes. */
@Composable
internal fun PracticeCoach(
    step: HelperPracticeStep,
    modifier: Modifier = Modifier,
) {
    val (title, body) = practiceCoach(step)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(COACH_SPACING)) {
        HelperCharacter(COACH_CHARACTER, plate = true)
        Text(
            stringResource(R.string.helper_tour_progress, step.ordinal + 1),
            color = Color.White,
            fontSize = COACH_BODY,
            lineHeight = COACH_BODY_LINE,
        )
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(COACH_CORNER),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(Modifier.padding(COACH_PADDING), verticalArrangement = Arrangement.spacedBy(COACH_SPACING)) {
                Text(
                    stringResource(title),
                    Modifier.semantics { heading() },
                    fontSize = COACH_TITLE,
                    lineHeight = COACH_TITLE_LINE,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(body), fontSize = COACH_BODY, lineHeight = COACH_BODY_LINE)
            }
        }
    }
}
