package com.danielealbano.androidremotecontrolmcp.ui.helper.bubble

import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** The saved vertical position of the helper bubble, in thousandths of the screen height above the bottom edge. */
internal interface BubblePositionStore {
    val position: Flow<Int?>

    suspend fun save(permille: Int)
}

/** [BubblePositionStore] kept with the app settings. */
@Singleton
internal class SettingsBubblePositionStore
    @Inject
    constructor(
        private val settings: SettingsRepository,
    ) : BubblePositionStore {
        override val position: Flow<Int?> = settings.helperBubblePosition

        override suspend fun save(permille: Int) = settings.updateHelperBubblePosition(permille)
    }

internal const val PERMILLE = 1000

/** The bubble offset above the bottom edge, kept on screen: at least [minOffset], [topReserve] below the top. */
internal fun clampBubbleOffset(
    offset: Int,
    screenHeight: Int,
    bubbleHeight: Int,
    minOffset: Int,
    topReserve: Int,
): Int = offset.coerceIn(minOffset, maxOf(minOffset, screenHeight - bubbleHeight - topReserve))

/** [offset] as thousandths of [screenHeight], so the position survives rotation and other screen sizes. */
internal fun bubblePermille(
    offset: Int,
    screenHeight: Int,
): Int = if (screenHeight <= 0) 0 else (offset.toLong() * PERMILLE / screenHeight).toInt().coerceIn(0, PERMILLE)

/** The offset in pixels for a saved [permille] position. */
internal fun bubbleOffset(
    permille: Int,
    screenHeight: Int,
): Int = (permille.coerceIn(0, PERMILLE).toLong() * screenHeight / PERMILLE).toInt()

/** Where the bubble sits on the right edge while the user drags it, in pixels above the bottom edge. */
internal class BubbleDragTracker(
    private val params: BubbleWindowParams,
    private val screenHeight: () -> Int,
    private val bubbleHeight: () -> Int,
) {
    /** Null until a position is restored or the bubble is dragged: the default position. */
    var offset: Int? = null
        private set

    fun restore(permille: Int?) {
        offset = permille?.let { clamp(bubbleOffset(it, screenHeight())) }
    }

    /** Follows the finger ([deltaY] in pixels, positive = down). */
    fun drag(deltaY: Float) {
        offset = clamp((offset ?: params.defaultBubbleOffset()) - deltaY.roundToInt())
    }

    /** The current position to save, or null when the bubble was never moved. */
    fun permille(): Int? = offset?.let { bubblePermille(it, screenHeight()) }

    private fun clamp(value: Int): Int =
        clampBubbleOffset(value, screenHeight(), bubbleHeight(), params.minBubbleOffset(), params.topReserve())
}
