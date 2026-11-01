package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig

/**
 * Where the API key saved for the previous AI provider lived. It is never read: that key must not be sent to OpenAI.
 * The OpenAI key a user saves lives under [ASSISTANT_API_KEY_CIPHERTEXT_KEY].
 */
internal val LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY = stringPreferencesKey("assistant_api_key_ciphertext")

/**
 * Settings migration for the switch to OpenAI, run by the "settings" DataStore before its first read: drops the key
 * saved for the previous AI provider and a stored model outside [AssistantConfig.MODELS] (which already reads as
 * [AssistantConfig.DEFAULT_MODEL]). Everything else is kept. Idempotent: a migrated store needs no further work.
 */
object AssistantProviderMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY in currentData || currentData.hasStaleModel()

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData
            .toMutablePreferences()
            .apply {
                remove(LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY)
                if (hasStaleModel()) remove(ASSISTANT_MODEL_KEY)
            }.toPreferences()

    override suspend fun cleanUp() = Unit

    private fun Preferences.hasStaleModel(): Boolean {
        val model = this[ASSISTANT_MODEL_KEY] ?: return false
        return model !in AssistantConfig.MODELS
    }
}
