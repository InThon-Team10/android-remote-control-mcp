package com.danielealbano.androidremotecontrolmcp.data.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadConfig2026
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Tink AES-256-GCM [Aead] whose keyset is stored by Tink in app-private shared preferences, encrypted with an
 * Android Keystore master key. Built lazily on first use (blocking I/O: call off the main thread); a failed build
 * is retried on the next call.
 */
class AndroidKeystoreAeadProvider(
    private val context: Context,
) {
    private val aead: Aead by lazy {
        // AndroidKeysetManager creates and parses keysets through Tink's global registry.
        AeadConfig.register()
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, KEYSET_NAME, PREF_FILE_NAME)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(AeadConfig2026.get(), Aead::class.java)
    }

    fun get(): Aead = aead

    private companion object {
        const val KEYSET_NAME = "assistant_api_key_keyset"
        const val PREF_FILE_NAME = "assistant_api_key_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://assistant_api_key_master"
    }
}
