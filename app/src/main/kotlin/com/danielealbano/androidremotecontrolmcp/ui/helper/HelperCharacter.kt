@file:Suppress("FunctionNaming")

package com.danielealbano.androidremotecontrolmcp.ui.helper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.theme.PrimaryContainerLight

private val PLATE_PADDING = 2.dp

/**
 * 콕콕이 as decorative art (the screen reader name always comes from the surrounding control or text). [plate]
 * adds a light circular backdrop in both themes: use it at 48dp or smaller or on blue surfaces.
 */
@Composable
internal fun HelperCharacter(
    size: Dp,
    modifier: Modifier = Modifier,
    state: HelperMascotState = HelperMascotState.AWAKE,
    plate: Boolean = false,
) {
    val art =
        when (state) {
            HelperMascotState.AWAKE -> R.drawable.helper_phone_mascot
            HelperMascotState.SLEEPING -> R.drawable.helper_phone_mascot_sleeping
            HelperMascotState.THINKING -> R.drawable.helper_phone_mascot_thinking
        }
    if (plate) {
        Box(
            modifier = modifier.size(size).clip(CircleShape).background(PrimaryContainerLight),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier.size(size - PLATE_PADDING * 2),
            )
        }
    } else {
        Image(painter = painterResource(art), contentDescription = null, modifier = modifier.size(size))
    }
}
