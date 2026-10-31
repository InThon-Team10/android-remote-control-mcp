package com.danielealbano.androidremotecontrolmcp.data.security

import com.danielealbano.androidremotecontrolmcp.data.repository.softwareAead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.ProviderException
import java.util.Base64

class TinkApiKeyCipherTest {
    private val aead = softwareAead()
    private val cipher = TinkApiKeyCipher { aead }

    @Test
    fun `round trip returns the original key`() {
        assertEquals("AIza-secret", cipher.decrypt(cipher.encrypt("AIza-secret")))
    }

    @Test
    fun `encrypt output never contains the plaintext`() {
        assertFalse(cipher.encrypt("AIza-secret").contains("AIza-secret"))
    }

    @Test
    fun `tampered ciphertext returns null`() {
        val raw = Base64.getDecoder().decode(cipher.encrypt("AIza-secret"))
        raw[raw.lastIndex] = (raw[raw.lastIndex].toInt() xor 1).toByte()
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(raw)))
    }

    @Test
    fun `decrypt with a different key returns null`() {
        val other = softwareAead()
        assertNull(TinkApiKeyCipher { other }.decrypt(cipher.encrypt("AIza-secret")))
    }

    @Test
    fun `ciphertext with other associated data returns null`() {
        val foreign = aead.encrypt("AIza-secret".toByteArray(), "other".toByteArray())
        assertNull(cipher.decrypt(Base64.getEncoder().encodeToString(foreign)))
    }

    @Test
    fun `malformed and empty input return null`() {
        assertNull(cipher.decrypt("not base64 !!"))
        assertNull(cipher.decrypt(""))
    }

    @Test
    fun `decrypt returns null when the aead provider throws`() {
        assertNull(TinkApiKeyCipher { throw ProviderException("keystore") }.decrypt(cipher.encrypt("k")))
        assertNull(TinkApiKeyCipher { throw IOException("prefs") }.decrypt(cipher.encrypt("k")))
    }
}
