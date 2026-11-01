package com.danielealbano.androidremotecontrolmcp.data.repository

import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.HelperFlag
import kotlinx.coroutines.flow.Flow

/**
 * Assistant settings slice, exposed through [SettingsRepository] and backed by the same "settings" DataStore.
 * Step-limit validation: [AssistantConfig.validateStepLimit].
 */
interface AssistantSettings : HelperBubbleSettings {
    /** Observes the configuration. `apiKeyConfigured` is true only when a stored key can be decrypted. */
    val config: Flow<AssistantConfig>

    suspend fun getConfig(): AssistantConfig

    /** Returns the decrypted API key, or null when unset or undecryptable. */
    suspend fun getApiKey(): String?

    /**
     * Stores the trimmed [key] encrypted.
     *
     * @throws IllegalArgumentException when [key] is blank.
     * @throws java.security.GeneralSecurityException when encryption fails; nothing is stored.
     * @throws java.security.ProviderException when the Keystore provider fails; nothing is stored.
     */
    suspend fun setApiKey(key: String)

    suspend fun clearApiKey()

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setVoiceEnabled(enabled: Boolean)

    /** @throws IllegalArgumentException when [model] is not in [AssistantConfig.MODELS]. */
    suspend fun setModel(model: String)

    /**
     * @throws IllegalArgumentException when [limit] is outside
     * [AssistantConfig.MIN_STEP_LIMIT]..[AssistantConfig.MAX_STEP_LIMIT].
     */
    suspend fun setStepLimit(limit: Int)

    suspend fun acceptPrivacyNotice()

    /** Stores one on/off helper preference; see [HelperFlag] for the meaning and default of each. */
    suspend fun setHelperFlag(
        flag: HelperFlag,
        value: Boolean,
    )
}
