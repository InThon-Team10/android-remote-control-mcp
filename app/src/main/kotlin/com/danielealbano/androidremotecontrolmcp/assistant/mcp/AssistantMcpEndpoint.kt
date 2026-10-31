package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.utils.NetworkUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import okhttp3.tls.HandshakeCertificates
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier

/** URL resolution and HTTP client construction for the assistant's loopback connection to the MCP server. */
internal object AssistantMcpEndpoint {
    const val LOOPBACK_HOST = "127.0.0.1"
    const val PATH = "/mcp"
    const val REQUEST_TIMEOUT_MS = 90_000L
    const val CONNECT_TIMEOUT_MS = 10_000L

    /**
     * `127.0.0.1` for LOCALHOST and NETWORK (a wildcard bind is reachable on loopback), the configured address for
     * SPECIFIC (loopback when still blank); `https` iff [ServerConfig.httpsEnabled].
     */
    fun url(config: ServerConfig): String {
        val host =
            when (config.bindingAddress) {
                BindingAddress.LOCALHOST, BindingAddress.NETWORK -> {
                    LOOPBACK_HOST
                }

                BindingAddress.SPECIFIC -> {
                    NetworkUtils.urlHost(config.specificBindAddress.trim().ifEmpty { LOOPBACK_HOST })
                }
            }
        val scheme = if (config.httpsEnabled) "https" else "http"
        return "$scheme://$host:${config.port}$PATH"
    }

    /**
     * Needed because a custom certificate may lack a loopback SAN. Accepts any host name, but only when the peer's
     * leaf certificate is identical to [certificate], so it cannot be satisfied without the server's private key.
     */
    fun pinnedHostnameVerifier(certificate: X509Certificate): HostnameVerifier =
        HostnameVerifier { _, session ->
            runCatching { session.peerCertificates.firstOrNull() == certificate }.getOrDefault(false)
        }

    /** Ktor client with the SSE plugin the SDK transport needs, 90 s timeouts, pinned TLS when [certificate] is set. */
    fun httpClient(certificate: X509Certificate?): HttpClient =
        HttpClient(OkHttp) {
            install(SSE)
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
            }
            if (certificate != null) {
                val tls = HandshakeCertificates.Builder().addTrustedCertificate(certificate).build()
                engine {
                    config {
                        sslSocketFactory(tls.sslSocketFactory(), tls.trustManager)
                        hostnameVerifier(pinnedHostnameVerifier(certificate))
                    }
                }
            }
        }
}
