package com.danielealbano.androidremotecontrolmcp.services.tunnel.arctunnel

import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** JSON GET/POST without blocking: calls are enqueued and cancelled together with the calling coroutine. */
object ArcTunnelHttp {
    private const val TAG = "MCP:ArcTunnelHttp"
    private const val ENROLL_TIMEOUT_S = 300L
    private const val ISSUE_TIMEOUT_S = 400L
    private const val MALFORMED = "malformed_response"
    private val JSON = "application/json".toMediaType()

    suspend fun <T> get(
        serializer: KSerializer<T>,
        client: OkHttpClient,
        url: String,
    ): T {
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        return decode(serializer, execute(client, request))
    }

    suspend fun <T> post(
        serializer: KSerializer<T>,
        client: OkHttpClient,
        url: String,
        body: String,
    ): T {
        val request =
            Request
                .Builder()
                .url(url)
                .post(body.toRequestBody(JSON))
                .build()
        return decode(serializer, execute(client, request))
    }

    fun enrollClient(client: OkHttpClient): OkHttpClient =
        client
            .newBuilder()
            .readTimeout(ENROLL_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(ENROLL_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    fun issueClient(client: OkHttpClient): OkHttpClient =
        client
            .newBuilder()
            .readTimeout(ISSUE_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(ISSUE_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

    /** Executes and returns the body of a 200; any other status is thrown as [ArcTunnelApiException]. */
    suspend fun execute(
        client: OkHttpClient,
        request: Request,
    ): String {
        val (code, body) = await(client.newCall(request))
        if (code != HttpURLConnection.HTTP_OK) fail(code, body)
        return body
    }

    /**
     * Suspends until [call] answered AND its body was read (both on OkHttp's thread, so `cancel()` aborts a
     * stalled body read too); cancelling the coroutine cancels the call. Returns the status and the body.
     */
    suspend fun await(call: Call): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        continuation.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        continuation.resumeWith(runCatching { response.use { it.code to it.body.string() } })
                    }
                },
            )
        }

    fun fail(
        status: Int,
        body: String,
    ): Nothing {
        val error =
            try {
                ArcTunnelJson.decodeFromString(ArcTunnelApiErrorBody.serializer(), body)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "undecodable error body (status $status): ${e.javaClass.simpleName}")
                ArcTunnelApiErrorBody()
            }
        val reason = error.reason.ifEmpty { "http_$status" }
        throw ArcTunnelApiException(reason, error.retryable, error.retryAfterSeconds, status)
    }

    /** A malformed 200 body is reported WITHOUT its content (it may carry a nonce or a chain). */
    private fun <T> decode(
        serializer: KSerializer<T>,
        body: String,
    ): T =
        try {
            ArcTunnelJson.decodeFromString(serializer, body)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "malformed answer from the tunnel service: ${e.javaClass.simpleName}")
            throw ArcTunnelApiException(MALFORMED, false, 0, HttpURLConnection.HTTP_OK)
        }
}
