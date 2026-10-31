package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("McpHelperServerSwitch")
class HelperServerSwitchTest {
    private val starter = mockk<AssistantServerStarter>()
    private val context = mockk<Context>(relaxed = true)

    @Test
    fun `ensureRunning delegates to the starter`() =
        runTest {
            coEvery { starter.ensureRunning(context) } returns Result.success(Unit)

            assertTrue(McpHelperServerSwitch(starter).ensureRunning(context).isSuccess)
        }

    @Test
    fun `stop starts the server service once`() {
        McpHelperServerSwitch(starter).stop(context)

        verify(exactly = 1) { context.startForegroundService(any()) }
    }
}
