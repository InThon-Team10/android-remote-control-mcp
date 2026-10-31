package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val ArcTunnelJson: Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

@Serializable
data class ArcTunnelNonceResponse(
    val nonce: String,
)

@Serializable
data class ArcTunnelEnrollRequest(
    val nonce: String,
    @SerialName("attestation_chain") val attestationChain: String,
    @SerialName("identity_csr") val identityCsr: String,
)

@Serializable
data class ArcTunnelEnrollResponse(
    val name: String,
    @SerialName("identity_cert") val identityCert: String,
    @SerialName("issue_nonce") val issueNonce: String,
)

@Serializable
data class ArcTunnelIssueRequest(
    val nonce: String,
    @SerialName("attestation_chain") val attestationChain: String,
    @SerialName("identity_csr") val identityCsr: String,
    @SerialName("tls_csr") val tlsCsr: String,
)

@Serializable
data class ArcTunnelIssueResponse(
    @SerialName("identity_cert") val identityCert: String,
    @SerialName("public_cert") val publicCert: String,
    val ca: String = "",
)

@Serializable
data class ArcTunnelApiErrorBody(
    val reason: String = "",
    val retryable: Boolean = false,
    @SerialName("retry_after_seconds") val retryAfterSeconds: Long = 0,
)

@Serializable
data class ArcTunnelOpenPayload(
    val token: String = "",
    val addr: String = "",
    @SerialName("use_https") val useHttps: Boolean = false,
    @SerialName("use_mtls") val useMtls: Boolean = false,
) {
    fun isValid(): Boolean = hasHeaderSafeToken() && addr.isNotEmpty() && (useHttps || !useMtls)

    /** The token is written into a request header: control characters would let the server inject headers/requests. */
    private fun hasHeaderSafeToken(): Boolean = token.isNotEmpty() && token.none(Char::isISOControl)
}

@Serializable
data class ArcTunnelRenewNudgePayload(
    val nonce: String = "",
)

/** Structured server error (PROTOCOL.md §2–§3): `{reason, retryable, retry_after_seconds}` + HTTP status. */
class ArcTunnelApiException(
    val reason: String,
    val retryable: Boolean,
    val retryAfterSec: Long,
    val status: Int,
) : RuntimeException("tunnel service: $reason (status $status)") {
    /**
     * The server-requested wait in ms: 0 when [retryAfterSec] is not positive, otherwise capped at
     * [ArcTunnelBackoff.MAX_MS] (the longest wait the tunnel client schedules); never overflows.
     */
    val retryAfterMs: Long
        get() =
            when {
                retryAfterSec <= 0 -> 0L
                retryAfterSec >= ArcTunnelBackoff.MAX_MS / MS_PER_S -> ArcTunnelBackoff.MAX_MS
                else -> retryAfterSec * MS_PER_S
            }

    private companion object {
        const val MS_PER_S = 1_000L
    }
}
