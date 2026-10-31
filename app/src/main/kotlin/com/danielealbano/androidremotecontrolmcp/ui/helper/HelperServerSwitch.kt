package com.danielealbano.androidremotecontrolmcp.ui.helper

import android.content.Context
import android.content.Intent
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantServerStarter
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import javax.inject.Inject

/** Starts and stops the MCP server for the helper; both need a [Context] of a visible UI component. */
interface HelperServerSwitch {
    /** Failures are `AssistantException`s. */
    suspend fun ensureRunning(context: Context): Result<Unit>

    /** Asks the server service to stop; idempotent, the service ignores a stop while it is not running. */
    fun stop(context: Context)
}

class McpHelperServerSwitch
    @Inject
    constructor(
        private val starter: AssistantServerStarter,
    ) : HelperServerSwitch {
        override suspend fun ensureRunning(context: Context): Result<Unit> = starter.ensureRunning(context)

        override fun stop(context: Context) {
            context.startForegroundService(
                Intent(context, McpServerService::class.java).apply { action = McpServerService.ACTION_STOP },
            )
        }
    }
