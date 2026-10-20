package com.danielealbano.androidremotecontrolmcp.e2e

import io.ktor.client.engine.okhttp.OkHttp

/** The PUBLIC side of a tunnel: what any Internet client does. */
object ArcTunnelPublicClient {
    /** An MCP client with the JVM's default trust store (production: a WebPKI certificate). */
    fun systemTrustMcpClient(
        publicUrl: String,
        bearerToken: String,
    ): McpClient = McpClient(publicUrl, bearerToken, McpClient.httpClient(OkHttp) {})
}
