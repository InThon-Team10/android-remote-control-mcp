package com.danielealbano.androidremotecontrolmcp.mcp

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.host
import io.ktor.server.request.port
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Derivation of the public base URL the client actually connected on, used by every absolute URL the
 * server hands out (OAuth metadata, share-content links). The host is taken from `X-Forwarded-Host` /
 * `Host` and the scheme from `X-Forwarded-Proto` / the listener scheme, so the URL is reachable across
 * all topologies (cloudflared, ngrok, Tailscale Funnel, router/DDNS).
 *
 * **Security rationale (forwarded-header trust) — by design:** the derivation trusts the client-settable
 * `X-Forwarded-*` headers, but the response is PER-CONNECTION, so a spoofed host only changes the
 * response sent back to the spoofer (no cross-client/cached poisoning): a token minted with a spoofed
 * `aud` is rejected at the real `/mcp` (aud-binding), the OAuth path requires a trusted tunnel, and
 * share links are returned to the calling agent over the same connection. A non-empty
 * `publicUrlOverride` pins the host and ignores forwarded headers entirely.
 */

private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443
private const val SCHEME_SEPARATOR = "://"
private const val MAX_PORT = 65535
private const val ASCII_LIMIT = 128

/**
 * Returns a normalized `scheme://host[:port]` from forwarded/Host headers.
 *
 * Each header value (first comma-separated entry) must be well formed to be used: `X-Forwarded-Proto`
 * exactly `http`/`https` (case-insensitive); `X-Forwarded-Host`/`Host` an authority of host (reg-name,
 * IPv4 or bracketed IPv6) with an optional `:port` in 1..65535 and no userinfo, path, query, fragment,
 * whitespace or control characters. A malformed value is treated exactly as an ABSENT header, so the
 * derivation falls through to the next source (`X-Forwarded-Host` -> `Host` -> listener host:port;
 * `X-Forwarded-Proto` -> listener scheme).
 */
fun deriveBaseUrl(call: ApplicationCall): String {
    val scheme =
        call.request.headers["X-Forwarded-Proto"]
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { it.equals("http", ignoreCase = true) || it.equals("https", ignoreCase = true) }
            ?: call.request.origin.scheme
    val forwardedHost =
        call.request.headers["X-Forwarded-Host"]
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { isValidAuthority(it) }
    // Use the raw Host header authority verbatim: it carries a port ONLY when the client actually
    // sent one. Reconstructing via host():port() would synthesize the local connection's scheme-default
    // port (e.g. http→80) when Host has none, producing a bogus `https://host:80` behind an HTTPS tunnel
    // (cloudflared/ngrok terminate TLS and forward plaintext, so the local scheme is http).
    val rawHost =
        call.request.headers["Host"]
            ?.substringBefore(',')
            ?.trim()
            ?.takeIf { isValidAuthority(it) }
    val hostPort = forwardedHost ?: rawHost ?: "${call.request.host()}:${call.request.port()}"
    return normalizeBaseUrl("$scheme://$hostPort")
}

private fun isHexOrIpv6Char(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' || c == ':' || c == '.'

private fun isRegNameChar(c: Char): Boolean = (c.code < ASCII_LIMIT && c.isLetterOrDigit()) || c in "._~-"

/** True when [value] is `host[:port]` (reg-name, IPv4 or bracketed IPv6; port 1..65535) and nothing else. */
private fun isValidAuthority(value: String): Boolean {
    val close = value.indexOf(']')
    val bracketed = value.startsWith("[")
    val hostEnd = if (bracketed) close + 1 else value.indexOf(':').let { if (it < 0) value.length else it }
    val rest = if (hostEnd in 1..value.length) value.substring(hostEnd) else null
    val hostValid =
        if (bracketed) {
            close > 1 && value.substring(1, close).let { it.contains(':') && it.all(::isHexOrIpv6Char) }
        } else {
            hostEnd > 0 && value.substring(0, hostEnd).all(::isRegNameChar)
        }
    return hostValid && rest != null && (rest.isEmpty() || isValidPortSuffix(rest))
}

private fun isValidPortSuffix(suffix: String): Boolean {
    val digits = suffix.removePrefix(":")
    val numeric = digits.isNotEmpty() && digits.all { it in '0'..'9' }
    return suffix.startsWith(":") && numeric && digits.toIntOrNull() in 1..MAX_PORT
}

/**
 * Lowercases scheme + host, strips a trailing `/`, and omits the port when it equals the scheme default
 * (80 http / 443 https). Returns `"$scheme://$hostport"`.
 */
fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim().removeSuffix("/")
    val schemeSep = trimmed.indexOf(SCHEME_SEPARATOR)
    if (schemeSep < 0) return trimmed.lowercase()
    val scheme = trimmed.substring(0, schemeSep).lowercase()
    val authority = trimmed.substring(schemeSep + SCHEME_SEPARATOR.length)
    val colon = authority.lastIndexOf(':')
    val host: String
    val port: Int?
    if (colon >= 0) {
        val portPart = authority.substring(colon + 1)
        val parsedPort = portPart.toIntOrNull()
        if (parsedPort != null) {
            host = authority.substring(0, colon).lowercase()
            port = parsedPort
        } else {
            host = authority.lowercase()
            port = null
        }
    } else {
        host = authority.lowercase()
        port = null
    }
    val defaultPort = if (scheme == "https") HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
    return if (port == null || port == defaultPort) {
        "$scheme://$host"
    } else {
        "$scheme://$host:$port"
    }
}

/** The override (when non-empty) wins over the request-derived base URL (also acts as a hostname pin). */
fun effectiveBaseUrl(
    call: ApplicationCall,
    override: String,
): String = override.trim().ifEmpty { null }?.let { normalizeBaseUrl(it) } ?: deriveBaseUrl(call)

/** Coroutine-context element carrying the per-request base URL into MCP tool handlers. */
class RequestBaseUrlElement(
    val baseUrl: String,
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RequestBaseUrlElement>
}

/** Reads the request base URL from the coroutine context, falling back when no request context is present. */
suspend fun currentRequestBaseUrl(fallback: suspend () -> String): String {
    val element = coroutineContext[RequestBaseUrlElement]
    return element?.baseUrl ?: fallback()
}

/** The canonical MCP resource identifier (`<base>/mcp`) used as the OAuth `aud`/`resource`. */
fun canonicalResource(baseUrl: String): String = "$baseUrl/mcp"
