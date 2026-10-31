package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.danielealbano.androidremotecontrolmcp.data.security.TinkApiKeyCipher
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.aead.AeadConfig2026
import com.google.crypto.tink.aead.PredefinedAeadParameters
import kotlinx.coroutines.Dispatchers

/** A software Tink AES-256-GCM [Aead] (no Android Keystore on the JVM). */
fun softwareAead(): Aead =
    KeysetHandle
        .generateNew(PredefinedAeadParameters.AES256_GCM, AeadConfig2026.get())
        .getPrimitive(AeadConfig2026.get(), Aead::class.java)

/** Builds an [AssistantSettingsImpl] backed by [softwareAead]. */
fun createAssistantSettings(
    dataStore: DataStore<Preferences>,
    changeLogger: SettingsChangeLogger,
): AssistantSettingsImpl {
    val aead = softwareAead()
    return AssistantSettingsImpl(dataStore, TinkApiKeyCipher { aead }, changeLogger, Dispatchers.Unconfined)
}
