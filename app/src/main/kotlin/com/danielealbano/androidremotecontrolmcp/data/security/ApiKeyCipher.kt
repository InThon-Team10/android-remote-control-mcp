package com.danielealbano.androidremotecontrolmcp.data.security

/** Reversible encryption of the OpenAI API key for storage in DataStore. */
interface ApiKeyCipher {
    /**
     * Returns the opaque Base64 text form of [plain].
     *
     * @throws java.security.GeneralSecurityException when the key or cipher is unavailable.
     * @throws java.security.ProviderException when the Keystore provider fails.
     */
    fun encrypt(plain: String): String

    /** Never throws. Returns null when [cipherText] is malformed, tampered with, or the key cannot decrypt it. */
    fun decrypt(cipherText: String): String?
}
