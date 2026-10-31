package com.danielealbano.androidremotecontrolmcp.data.repository

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import java.net.URL

/** Validates the structure of a non-blank public URL override; [url] is returned unchanged on success. */
internal fun validateParsedPublicUrl(url: String): Result<String> =
    try {
        val parsed = URL(url)
        if (parsed.protocol != "http" && parsed.protocol != "https") {
            Result.failure(IllegalArgumentException("URL must use http or https protocol"))
        } else if (parsed.host.isNullOrEmpty()) {
            Result.failure(IllegalArgumentException("URL must include a host"))
        } else if (parsed.port > ServerConfig.MAX_PORT) {
            Result.failure(IllegalArgumentException("URL port must be at most ${ServerConfig.MAX_PORT}"))
        } else if (parsed.query != null || parsed.ref != null) {
            Result.failure(IllegalArgumentException("URL must not include a query or fragment"))
        } else {
            Result.success(url)
        }
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Result.failure(IllegalArgumentException("Invalid URL format: ${e.message}"))
    }
