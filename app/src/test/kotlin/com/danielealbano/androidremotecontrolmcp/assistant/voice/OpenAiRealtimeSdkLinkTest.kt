package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.openai.core.http.WebSocketWriteNotAttempted
import com.openai.helpers.RealtimeConnection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("OpenAiRealtimeGateway.SdkLink")
class OpenAiRealtimeSdkLinkTest {
    private val connection = mockk<RealtimeConnection>()

    private fun TestScope.link(): OpenAiRealtimeGateway.SdkLink {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return OpenAiRealtimeGateway.SdkLink(connection, this, dispatcher)
    }

    @Test
    fun `a busy connection is retried and the event is sent`() =
        runTest {
            var calls = 0
            every { connection.send(any()) } answers {
                if (calls++ == 0) throw WebSocketWriteNotAttempted.Busy("busy")
            }

            link().send(RealtimeProtocol.responseCreate())

            verify(exactly = 2) { connection.send(any()) }
        }

    @Test
    fun `a connection busy past the retry window fails the send`() =
        runTest {
            every { connection.send(any()) } throws WebSocketWriteNotAttempted.Busy("busy")

            val failure = runCatching { link().send(RealtimeProtocol.responseCreate()) }.exceptionOrNull()

            assertTrue(failure is WebSocketWriteNotAttempted.Busy)
        }
}
