package com.danielealbano.androidremotecontrolmcp.services.storage

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/** A download connection refused because its address is on the local network ("Block Local Network Downloads"). */
internal class LocalNetworkDownloadBlockedException(
    endpoint: SocketAddress,
) : IOException(
        "connection to ${describe(endpoint)} refused: it is a local network address and " +
            "'Block Local Network Downloads' is enabled in the storage settings",
    ) {
    private companion object {
        fun describe(endpoint: SocketAddress): String =
            (endpoint as? InetSocketAddress)
                ?.let { "${it.address?.hostAddress ?: it.hostString}:${it.port}" }
                ?: endpoint.toString()
    }
}

/**
 * Creates sockets that refuse to connect to an address [isBlocked] rejects. The check runs on the resolved address
 * right before connecting, so it covers every hop of a download: host names, IP literals (which OkHttp connects to
 * without a DNS lookup) and redirects. It only sees the real target on direct connections, which is why
 * [DownloadHttpClientImpl] disables proxies while the block is on.
 */
internal class LocalNetworkBlockingSocketFactory(
    private val isBlocked: (InetAddress) -> Boolean = ::isLocalNetworkAddress,
) : SocketFactory() {
    override fun createSocket(): Socket = BlockingSocket(isBlocked)

    override fun createSocket(
        host: String,
        port: Int,
    ): Socket = connected(InetSocketAddress(host, port), null)

    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = connected(InetSocketAddress(host, port), InetSocketAddress(localHost, localPort))

    override fun createSocket(
        host: InetAddress,
        port: Int,
    ): Socket = connected(InetSocketAddress(host, port), null)

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int,
    ): Socket = connected(InetSocketAddress(address, port), InetSocketAddress(localAddress, localPort))

    private fun connected(
        endpoint: InetSocketAddress,
        local: InetSocketAddress?,
    ): Socket {
        val socket = createSocket()
        try {
            if (local != null) socket.bind(local)
            socket.connect(endpoint)
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        return socket
    }

    private class BlockingSocket(
        private val isBlocked: (InetAddress) -> Boolean,
    ) : Socket() {
        override fun connect(
            endpoint: SocketAddress,
            timeout: Int,
        ) {
            // An unresolved endpoint has no address to check: refuse it rather than let the platform resolve it.
            val address = (endpoint as? InetSocketAddress)?.address
            if (address == null || isBlocked(address)) throw LocalNetworkDownloadBlockedException(endpoint)
            super.connect(endpoint, timeout)
        }
    }
}
