package com.danielealbano.androidremotecontrolmcp.services.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URL

@DisplayName("sanitizeUrlForLog")
class UrlLogSanitizerTest {
    @Test
    fun `strips userinfo, query and fragment`() {
        val url = URL("https://user:secret@example.com/dir/file.bin?token=abc#frag")

        assertEquals("https://example.com/dir/file.bin", sanitizeUrlForLog(url))
    }

    @Test
    fun `keeps an explicit port`() {
        assertEquals("http://example.com:8080/a", sanitizeUrlForLog(URL("http://example.com:8080/a?x=1")))
    }

    @Test
    fun `keeps IPv6 host brackets`() {
        assertEquals("http://[2001:db8::1]:81/f", sanitizeUrlForLog(URL("http://[2001:db8::1]:81/f")))
    }

    @Test
    fun `handles a URL without a path`() {
        assertEquals("https://example.com", sanitizeUrlForLog(URL("https://example.com?token=abc")))
    }

    @Test
    fun `removes control characters`() {
        assertEquals("http://example.com/ab", sanitizeUrlForLog(URL("http://example.com/a\nb")))
    }

    @Test
    fun `removes C1 control characters`() {
        assertEquals("http://example.com/ab", sanitizeUrlForLog(URL("http://example.com/a\u0085b")))
        assertEquals("http://example.com/ab", sanitizeUrlForLog(URL("http://example.com/a\u0080b")))
    }
}
