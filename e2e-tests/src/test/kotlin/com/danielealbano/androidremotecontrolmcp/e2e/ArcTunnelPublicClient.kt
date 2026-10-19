package com.danielealbano.androidremotecontrolmcp.e2e

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

/** The PUBLIC side of a tunnel: what any Internet client does, minus the DNS the e2e replaces. */
object ArcTunnelPublicClient {
    private const val READ_LIMIT = 64 * 1024

    /** `GET /health` through the edge over one raw TLS connection; returns the HTTP status line + body. */
    fun health(
        stack: ArcTunnelStack,
        fqdn: String,
    ): String {
        val socket: SSLSocket = stack.connectTls(fqdn)
        socket.use {
            it.outputStream.write("GET /health HTTP/1.1\r\nHost: $fqdn\r\nConnection: close\r\n\r\n".toByteArray())
            it.outputStream.flush()
            return it.inputStream.readNBytes(READ_LIMIT).decodeToString()
        }
    }

    /** An MCP client whose HTTPS goes to the local relay with the real name as SNI/Host, trusting the Pebble root. */
    fun stackMcpClient(
        stack: ArcTunnelStack,
        fqdn: String,
        bearerToken: String,
    ): McpClient {
        val trust = stack.pebbleTrustManager()
        val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }
        val http: HttpClient =
            McpClient.httpClient(OkHttp) {
                preconfigured =
                    OkHttpClient
                        .Builder()
                        .dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
                        .sslSocketFactory(ctx.socketFactory, trust)
                        .build()
            }
        return McpClient("https://$fqdn:${ArcTunnelStack.EDGE_HOST_PORT}", bearerToken, http)
    }

    /** An MCP client with the JVM's default trust store (production: a WebPKI certificate). */
    fun systemTrustMcpClient(
        publicUrl: String,
        bearerToken: String,
    ): McpClient = McpClient(publicUrl, bearerToken, McpClient.httpClient(OkHttp) {})
}
