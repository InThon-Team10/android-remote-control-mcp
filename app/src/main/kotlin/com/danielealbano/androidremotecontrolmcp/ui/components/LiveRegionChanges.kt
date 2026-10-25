package com.danielealbano.androidremotecontrolmcp.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Whether [value] has differed from the first value this composition showed once [ready]; it stays true once true.
 *
 * A live region set on a node from the start is also read whenever the node is composed again (a tab shown, a return
 * from another screen, a rotation): Compose reports a new node's first layout as a content change from it, and TalkBack
 * reads a live region on any content change. Switching the live region on only from the first change on limits the
 * announcements to real changes. [ready] is false while [value] is built from a placeholder (a setting not read yet),
 * which is not a state to compare with.
 */
@Composable
fun rememberChangedSinceShown(
    value: Any?,
    ready: Boolean = true,
): Boolean = remember { ChangedSinceShown() }.update(value, ready)

/** The state behind [rememberChangedSinceShown]: the first value recorded once ready, and whether one differed. */
internal class ChangedSinceShown {
    private var first: Any? = NotShownYet
    private var changed = false

    /** Records [value] as the first one when [ready] and none is recorded yet; returns whether a later one differed. */
    fun update(
        value: Any?,
        ready: Boolean,
    ): Boolean {
        if (ready) {
            if (first === NotShownYet) {
                first = value
            } else if (value != first) {
                changed = true
            }
        }
        return changed
    }

    /** The first value before any was recorded. */
    private object NotShownYet
}
