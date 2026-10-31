package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.security.ApiKeyCipher
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal val ASSISTANT_ENABLED_KEY = booleanPreferencesKey("assistant_enabled")
internal val ASSISTANT_VOICE_ENABLED_KEY = booleanPreferencesKey("assistant_voice_enabled")
internal val ASSISTANT_MODEL_KEY = stringPreferencesKey("assistant_model")
internal val ASSISTANT_STEP_LIMIT_KEY = intPreferencesKey("assistant_step_limit")
internal val ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY = booleanPreferencesKey("assistant_privacy_notice_accepted")
internal val ASSISTANT_API_KEY_CIPHERTEXT_KEY = stringPreferencesKey("assistant_api_key_ciphertext")

/**
 * [AssistantSettings] backed by the "settings" Preferences DataStore. Changes are coalesced-logged through
 * [SettingsChangeLogger]; for the API key the render lambda ignores both values (they are ciphertexts, compared
 * only so that replacing a key is never dropped as a no-op).
 */
class AssistantSettingsImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val cipher: ApiKeyCipher,
        private val settingsChangeLogger: SettingsChangeLogger,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : AssistantSettings {
        override val config: Flow<AssistantConfig> =
            dataStore.data
                .map { prefs ->
                    val configured = prefs[ASSISTANT_API_KEY_CIPHERTEXT_KEY]?.let(cipher::decrypt) != null
                    prefs.toConfig(apiKeyConfigured = configured)
                }.distinctUntilChanged()
                .flowOn(ioDispatcher)

        override suspend fun getConfig(): AssistantConfig = config.first()

        override suspend fun getApiKey(): String? {
            val ciphertext = dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY]
            return withContext(ioDispatcher) { ciphertext?.let(cipher::decrypt) }
        }

        override suspend fun setApiKey(key: String) {
            val trimmed = key.trim()
            require(trimmed.isNotEmpty()) { "API key cannot be blank" }
            writeApiKeyCiphertext(withContext(ioDispatcher) { cipher.encrypt(trimmed) })
        }

        override suspend fun clearApiKey() = writeApiKeyCiphertext(null)

        override suspend fun setEnabled(enabled: Boolean) =
            dataStore.updateToggle(ASSISTANT_ENABLED_KEY, enabled, settingsChangeLogger, ToggleLabel("Assistant"))

        override suspend fun setVoiceEnabled(enabled: Boolean) =
            dataStore.updateToggle(
                ASSISTANT_VOICE_ENABLED_KEY,
                enabled,
                settingsChangeLogger,
                ToggleLabel("Assistant voice input"),
            )

        override suspend fun acceptPrivacyNotice() =
            dataStore.updateToggle(
                ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY,
                true,
                settingsChangeLogger,
                ToggleLabel("Assistant privacy notice", onWord = "accepted"),
            )

        override suspend fun setModel(model: String) {
            require(model in AssistantConfig.MODELS) { "Unknown assistant model" }
            dataStore.edit { prefs ->
                val old = prefs[ASSISTANT_MODEL_KEY] ?: AssistantConfig.DEFAULT_MODEL
                prefs[ASSISTANT_MODEL_KEY] = model
                settingsChangeLogger.submit(ASSISTANT_MODEL_KEY.name, old, model) { o, n ->
                    "Assistant model changed $o → $n"
                }
            }
        }

        override suspend fun setStepLimit(limit: Int) {
            val valid = AssistantConfig.validateStepLimit(limit).getOrThrow()
            dataStore.edit { prefs ->
                val old = prefs[ASSISTANT_STEP_LIMIT_KEY] ?: AssistantConfig.DEFAULT_STEP_LIMIT
                prefs[ASSISTANT_STEP_LIMIT_KEY] = valid
                settingsChangeLogger.submit(ASSISTANT_STEP_LIMIT_KEY.name, old.toString(), valid.toString()) { o, n ->
                    "Assistant step limit changed $o → $n"
                }
            }
        }

        private suspend fun writeApiKeyCiphertext(ciphertext: String?) {
            dataStore.edit { prefs ->
                val old = prefs[ASSISTANT_API_KEY_CIPHERTEXT_KEY].orEmpty()
                if (ciphertext == null) {
                    prefs.remove(ASSISTANT_API_KEY_CIPHERTEXT_KEY)
                } else {
                    prefs[ASSISTANT_API_KEY_CIPHERTEXT_KEY] = ciphertext
                }
                settingsChangeLogger.submit(
                    "assistant_api_key",
                    old,
                    ciphertext.orEmpty(),
                ) { _, n -> if (n.isEmpty()) "Assistant API key cleared" else "Assistant API key changed" }
            }
        }
    }

/** Wording of a toggle's log line: "<subject> <onWord|offWord>". */
private data class ToggleLabel(
    val subject: String,
    val onWord: String = "enabled",
    val offWord: String = "disabled",
)

private suspend fun DataStore<Preferences>.updateToggle(
    key: Preferences.Key<Boolean>,
    enabled: Boolean,
    logger: SettingsChangeLogger,
    label: ToggleLabel,
) {
    edit { prefs ->
        val old = prefs[key] ?: false
        prefs[key] = enabled
        logger.submitToggle(key.name, old, enabled, label.subject, label.onWord, label.offWord)
    }
}

private fun Preferences.toConfig(apiKeyConfigured: Boolean): AssistantConfig {
    val storedModel = this[ASSISTANT_MODEL_KEY]
    val storedLimit = this[ASSISTANT_STEP_LIMIT_KEY]
    return AssistantConfig(
        enabled = this[ASSISTANT_ENABLED_KEY] ?: false,
        voiceEnabled = this[ASSISTANT_VOICE_ENABLED_KEY] ?: false,
        model = storedModel?.takeIf { it in AssistantConfig.MODELS } ?: AssistantConfig.DEFAULT_MODEL,
        stepLimit =
            storedLimit?.takeIf { AssistantConfig.validateStepLimit(it).isSuccess }
                ?: AssistantConfig.DEFAULT_STEP_LIMIT,
        privacyNoticeAccepted = this[ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY] ?: false,
        apiKeyConfigured = apiKeyConfigured,
    )
}
