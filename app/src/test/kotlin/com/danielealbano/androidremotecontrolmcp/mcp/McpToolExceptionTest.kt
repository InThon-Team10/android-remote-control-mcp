package com.danielealbano.androidremotecontrolmcp.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("McpToolException messages")
class McpToolExceptionTest {
    private val controls =
        listOf("\r", "\n", "\r\n", "\u0000", "\t", "\u001B", "\u007F", "\u0085", "\u009F", "\u2028", "\u2029")

    private fun allKinds(message: String): List<McpToolException> =
        listOf(
            McpToolException.InvalidParams(message),
            McpToolException.InternalError(message),
            McpToolException.PermissionDenied(message),
            McpToolException.NodeNotFound(message),
            McpToolException.ActionFailed(message),
            McpToolException.Timeout(message),
            McpToolException.PrivacyModeUnavailable(message),
        )

    private fun hasControl(text: String): Boolean = text.any { it.isISOControl() || it == '\u2028' || it == '\u2029' }

    @Test
    fun `control characters of an echoed value never survive in any exception kind`() {
        for (control in controls) {
            for (exception in allKinds("bad${control}value")) {
                val message = exception.message.orEmpty()
                val kind = exception::class.simpleName
                assertFalse(hasControl(message), "$kind kept U+${control[0].code.toString(HEX_RADIX)}: $message")
                assertTrue(message.startsWith("bad") && message.endsWith("value"), message)
            }
        }
    }

    @Test
    fun `a message without control characters is unchanged`() {
        val text = "Parameter 'x' must be a number, got string: 'abc é 😀'"
        for (exception in allKinds(text)) {
            assertEquals(text, exception.message)
        }
    }

    @Test
    fun `the cause is preserved`() {
        val cause = IllegalStateException("boom")
        assertEquals(cause, McpToolException.ActionFailed("a\nb", cause).cause)
    }

    private companion object {
        const val HEX_RADIX = 16
    }
}
