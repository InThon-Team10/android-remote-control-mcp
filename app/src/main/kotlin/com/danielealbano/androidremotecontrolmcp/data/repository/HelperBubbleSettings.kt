package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where the user left the helper bubble on the right screen edge; part of [AssistantSettings]. */
interface HelperBubbleSettings {
    /** Thousandths of the screen height above the bottom edge; null until the user moves the bubble. */
    val helperBubblePosition: Flow<Int?>

    /** Persists the bubble position, clamped to 0..1000. */
    suspend fun updateHelperBubblePosition(permille: Int)
}

/** [HelperBubbleSettings] in the shared "settings" Preferences DataStore. */
internal class DataStoreHelperBubbleSettings(
    private val dataStore: DataStore<Preferences>,
) : HelperBubbleSettings {
    override val helperBubblePosition: Flow<Int?> = dataStore.data.map { prefs -> prefs[HELPER_BUBBLE_POSITION_KEY] }

    override suspend fun updateHelperBubblePosition(permille: Int) {
        dataStore.edit { prefs -> prefs[HELPER_BUBBLE_POSITION_KEY] = permille.coerceIn(0, MAX_POSITION) }
    }

    private companion object {
        const val MAX_POSITION = 1000
    }
}

internal val HELPER_BUBBLE_POSITION_KEY = intPreferencesKey("helper_bubble_position_permille")
