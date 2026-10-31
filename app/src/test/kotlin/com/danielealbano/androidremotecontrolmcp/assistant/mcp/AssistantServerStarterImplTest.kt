package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantServerStarterImplTest {
    private val status = MutableStateFlow<ServerStatus>(ServerStatus.Stopped)
    private val context = mockk<Context>(relaxed = true)
    private val starter = AssistantServerStarterImpl()
    private val running = ServerStatus.Running(8080, "127.0.0.1")

    @BeforeEach
    fun setUp() {
        mockkObject(McpServerService.Companion)
        every { McpServerService.serverStatus } returns status
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(McpServerService.Companion)
    }

    private fun failure(result: Result<Unit>): AssistantError = (result.exceptionOrNull() as AssistantException).error

    @Test
    fun `already running returns success without starting`() =
        runTest {
            status.value = running
            assertTrue(starter.ensureRunning(context).isSuccess)
            verify(exactly = 0) { context.startForegroundService(any()) }
        }

    @Test
    fun `stopped starts service then Running resolves success`() =
        runTest {
            val result = async { starter.ensureRunning(context) }
            runCurrent()
            status.value = ServerStatus.Starting
            runCurrent()
            status.value = running
            assertTrue(result.await().isSuccess)
            verify(exactly = 1) { context.startForegroundService(any()) }
        }

    @Test
    fun `starting status still issues idempotent start and awaits Running`() =
        runTest {
            status.value = ServerStatus.Starting
            val result = async { starter.ensureRunning(context) }
            runCurrent()
            status.value = running
            assertTrue(result.await().isSuccess)
            verify(exactly = 1) { context.startForegroundService(any()) }
        }

    @Test
    fun `Error status while awaiting maps to ServerStartFailed with message`() =
        runTest {
            val result = async { starter.ensureRunning(context) }
            runCurrent()
            status.value = ServerStatus.Error("port in use")
            assertEquals(AssistantError.ServerStartFailed("port in use"), failure(result.await()))
        }

    @Test
    fun `stale Error from earlier start is skipped`() =
        runTest {
            status.value = ServerStatus.Error("old")
            val result = async { starter.ensureRunning(context) }
            runCurrent()
            assertFalse(result.isCompleted)
            status.value = ServerStatus.Starting
            runCurrent()
            status.value = running
            assertTrue(result.await().isSuccess)
        }

    @Test
    fun `new identical Error after Starting still fails`() =
        runTest {
            status.value = ServerStatus.Error("x")
            val result = async { starter.ensureRunning(context) }
            runCurrent()
            status.value = ServerStatus.Starting
            runCurrent()
            status.value = ServerStatus.Error("x")
            assertEquals(AssistantError.ServerStartFailed("x"), failure(result.await()))
        }

    @Test
    fun `timeout after 15 s maps to ServerStartFailed`() =
        runTest {
            status.value = ServerStatus.Starting
            val result = async { starter.ensureRunning(context) }
            advanceTimeBy(14_999)
            assertFalse(result.isCompleted)
            advanceUntilIdle()
            assertTrue(failure(result.await()) is AssistantError.ServerStartFailed)
        }

    @Test
    fun `ForegroundServiceStartNotAllowedException maps to ServerStartBlocked`() =
        runTest {
            every { context.startForegroundService(any()) } throws mockk<ForegroundServiceStartNotAllowedException>()
            assertEquals(AssistantError.ServerStartBlocked, failure(starter.ensureRunning(context)))
        }
}
