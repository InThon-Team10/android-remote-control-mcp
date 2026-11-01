package com.danielealbano.androidremotecontrolmcp.data.security

import com.google.crypto.tink.Aead
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64

/**
 * [ApiKeyCipher] on a Tink [Aead] (AES-256-GCM). [aead] is resolved on every call, so a Keystore failure surfaces
 * per call instead of at construction. The ciphertext is bound to [ASSOCIATED_DATA].
 */
class TinkApiKeyCipher(
    private val aead: () -> Aead,
) : ApiKeyCipher {
    override fun encrypt(plain: String): String =
        Base64.getEncoder().encodeToString(aead().encrypt(plain.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA))

    override fun decrypt(cipherText: String): String? =
        try {
            String(aead().decrypt(Base64.getDecoder().decode(cipherText), ASSOCIATED_DATA), Charsets.UTF_8)
        } catch (ignored: GeneralSecurityException) {
            null
        } catch (ignored: ProviderException) {
            null
        } catch (ignored: IOException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }

    private companion object {
        val ASSOCIATED_DATA: ByteArray = "assistant_api_key".toByteArray(Charsets.UTF_8)
    }
}
