package com.danielealbano.androidremotecontrolmcp.services.storage

import java.net.URL

private val CONTROL_CHAR_REGEX = Regex("\\p{Cntrl}")

/**
 * Log-safe form of [url]: `scheme://host[:port]/path`, without userinfo, query or fragment (they routinely carry
 * credentials or tokens) and without control characters.
 */
internal fun sanitizeUrlForLog(url: URL): String {
    val port = if (url.port == -1) "" else ":${url.port}"
    return "${url.protocol}://${url.host}$port${url.path}".replace(CONTROL_CHAR_REGEX, "")
}

/**
 * Log line for a finished download, with the source URL in its [sanitizeUrlForLog] form and the [destination]
 * stripped of control characters (it carries a caller-supplied path that could otherwise forge a log line).
 */
internal fun downloadLogMessage(
    bytes: Long,
    url: URL,
    destination: String,
): String {
    val safeDestination = destination.filterNot { it.isISOControl() }
    return "Downloaded $bytes bytes from ${sanitizeUrlForLog(url)} to $safeDestination"
}
