package com.danielealbano.androidremotecontrolmcp.assistant.openai

import com.openai.core.LogLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@DisplayName("OpenAiSdkClientProvider")
class OpenAiSdkClientProviderTest {
    private val provider = OpenAiSdkClientProvider()

    /** Asking for a new key closes the cached client; the one built for it is then closed too. */
    @AfterEach
    fun tearDown() {
        provider.client("teardown-key").close()
    }

    @Test
    fun `the same key returns the same client and options`() {
        val client = provider.client("key-a")
        val options = provider.clientOptions("key-a")

        assertSame(client, provider.client("key-a"))
        assertSame(options, provider.clientOptions("key-a"))
    }

    @Test
    fun `another key closes the previous client and builds a new one`() {
        val firstClient = provider.client("key-a")
        val firstOptions = provider.clientOptions("key-a")

        val secondClient = provider.client("key-b")
        val secondOptions = provider.clientOptions("key-b")

        assertNotSame(firstClient, secondClient)
        assertNotSame(firstOptions, secondOptions)
        assertTrue((firstOptions.streamHandlerExecutor as ExecutorService).isShutdown)
        assertFalse((secondOptions.streamHandlerExecutor as ExecutorService).isShutdown)
        assertEquals(Optional.of("key-b"), secondOptions.apiKey())
    }

    @Test
    fun `options carry the given key, two retries, short timeouts and no SDK logging`() {
        val options = provider.clientOptions("key-a")

        assertEquals(Optional.of("key-a"), options.apiKey())
        assertEquals(2, options.maxRetries)
        assertEquals(Duration.ofSeconds(10), options.timeout.connect())
        assertEquals(Duration.ofSeconds(30), options.timeout.request())
        assertEquals(LogLevel.OFF, options.logLevel)
        assertEquals("https://api.openai.com/v1", options.baseUrl())
    }

    @Test
    fun `a blank key is rejected`() {
        listOf("", "   ").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) { provider.client(key) }
            assertThrows(IllegalArgumentException::class.java) { provider.clientOptions(key) }
        }
    }

    @Test
    fun `concurrent callers with one key share a single client`() {
        val pool = Executors.newFixedThreadPool(THREADS)
        try {
            val clients =
                pool
                    .invokeAll(List(THREADS) { Callable { provider.client("key-a") } })
                    .map { it.get() }

            assertEquals(1, clients.distinct().size)
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        const val THREADS = 8
    }
}
