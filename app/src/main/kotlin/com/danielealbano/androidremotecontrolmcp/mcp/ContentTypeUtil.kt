package com.danielealbano.androidremotecontrolmcp.mcp

import com.danielealbano.androidremotecontrolmcp.services.sharing.LinkEntry
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes

/**
 * Parses [mimeType] into a [ContentType], falling back to `application/octet-stream` when it is malformed.
 *
 * A capability-link blob's MIME can originate from an arbitrary sharing app's `intent.type`, so a bad value
 * must not crash the `/s/{token}` handler with a [ContentType.parse] exception (HTTP 500). A value [ContentType.parse]
 * accepts but that carries control characters (e.g. NUL) is rejected too: it cannot be sent as a `Content-Type`
 * header, so the response would fail.
 */
internal fun contentTypeOrOctetStream(mimeType: String): ContentType =
    if (mimeType.any { it.isISOControl() }) {
        ContentType.Application.OctetStream
    } else {
        runCatching { ContentType.parse(mimeType) }.getOrDefault(ContentType.Application.OctetStream)
    }

/**
 * Serves a capability-link [entry] under its declared type (see [contentTypeOrOctetStream]) with
 * `X-Content-Type-Options: nosniff`, so a browser never reinterprets the blob (e.g. as HTML) on the server's origin.
 */
internal suspend fun ApplicationCall.respondSharedBlob(entry: LinkEntry) {
    response.header("X-Content-Type-Options", "nosniff")
    respondBytes(entry.bytes, contentTypeOrOctetStream(entry.mimeType), HttpStatusCode.OK)
}
