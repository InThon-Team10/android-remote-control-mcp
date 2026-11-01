package com.danielealbano.androidremotecontrolmcp.assistant.openai

import com.openai.client.OpenAIClient
import com.openai.errors.OpenAIIoException
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import com.openai.services.blocking.ResponseService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@DisplayName("OpenAiSdkResponsesGateway")
class OpenAiSdkResponsesGatewayTest {
    private val responses = mockk<ResponseService>()
    private val client = mockk<OpenAIClient> { every { responses() } returns responses }
    private val clients = mockk<OpenAiClientProvider> { every { client("key") } returns client }
    private val gateway = OpenAiSdkResponsesGateway(clients, Dispatchers.IO)
    private val params =
        ResponseCreateParams
            .builder()
            .model("m")
            .input("hi")
            .build()

    @Test
    fun `create calls the blocking client of the key with the params`() =
        runBlocking {
            val response = mockk<Response>()
            every { responses.create(params) } returns response

            assertSame(response, gateway.create("key", params))
            verify(exactly = 1) { clients.client("key") }
        }

    @Test
    fun `SDK failures are rethrown untouched`() =
        runBlocking {
            val failure = OpenAIIoException("down")
            every { responses.create(params) } throws failure

            val thrown = assertThrows<OpenAIIoException> { runBlocking { gateway.create("key", params) } }

            assertSame(failure, thrown)
        }

    @Test
    fun `cancelling the caller interrupts the blocking call and reports the cancellation`() =
        runBlocking {
            val started = CountDownLatch(1)
            val interrupted = CountDownLatch(1)
            every { responses.create(params) } answers {
                started.countDown()
                try {
                    Thread.sleep(BLOCK_MS)
                    mockk()
                } catch (error: InterruptedException) {
                    interrupted.countDown()
                    // What the SDK's OkHttp transport surfaces for an interrupted call.
                    throw OpenAIIoException("Request failed", InterruptedIOException(error.message))
                }
            }
            val call = async(Dispatchers.Default) { gateway.create("key", params) }
            assertTrue(started.await(WAIT_SECONDS, TimeUnit.SECONDS))

            call.cancel()

            assertThrows<CancellationException> { runBlocking { call.await() } }
            assertTrue(interrupted.await(WAIT_SECONDS, TimeUnit.SECONDS))
        }

    private companion object {
        const val BLOCK_MS = 30_000L
        const val WAIT_SECONDS = 5L
    }
}
