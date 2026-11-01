package com.danielealbano.androidremotecontrolmcp.mcp

/**
 * Sealed exception hierarchy for MCP tool errors.
 *
 * When thrown from a tool's `execute` method, the SDK catches
 * this exception and returns it as `CallToolResult(isError = true)`.
 *
 * Each subclass classifies a specific failure mode.
 *
 * Messages routinely echo client-supplied values, so control characters and line separators are
 * replaced by a visible unicode escape: an error message is always a single line.
 */
sealed class McpToolException(
    message: String,
    cause: Throwable? = null,
) : Exception(message.escapeControlCharacters(), cause) {
    class InvalidParams(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)

    class InternalError(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)

    class PermissionDenied(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)

    class NodeNotFound(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)

    class ActionFailed(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)

    /**
     * Thrown when a tool operation exceeds its time limit.
     *
     * Note: The SDK wraps all tool exceptions as `CallToolResult(isError = true)` with the
     * message in `TextContent`, so the specific subclass is used for logging granularity
     * and internal classification, not wire-level error codes.
     */
    class Timeout(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)

    /**
     * Thrown when Privacy Mode is enabled with model-backed categories but the detection model is
     * missing, unloadable, or failed mid-call. The tool returns NO device data (fail-closed); the
     * message tells the agent why Privacy Mode is unavailable.
     */
    class PrivacyModeUnavailable(
        message: String,
        cause: Throwable? = null,
    ) : McpToolException(message, cause)
}

private fun Char.isLineBreaking(): Boolean = isISOControl() || this == '\u2028' || this == '\u2029'

/**
 * Replaces control characters and line separators of a client-supplied value by a visible
 * unicode escape so the text it is echoed in stays on a single line. No-op for other text.
 */
internal fun String.escapeControlCharacters(): String {
    if (none { it.isLineBreaking() }) return this
    val escaped = StringBuilder(length + ESCAPE_HEADROOM)
    for (c in this) {
        if (c.isLineBreaking()) {
            escaped.append("\\u").append(c.code.toString(HEX_RADIX).padStart(HEX_WIDTH, '0'))
        } else {
            escaped.append(c)
        }
    }
    return escaped.toString()
}

private const val ESCAPE_HEADROOM = 16
private const val HEX_RADIX = 16
private const val HEX_WIDTH = 4
