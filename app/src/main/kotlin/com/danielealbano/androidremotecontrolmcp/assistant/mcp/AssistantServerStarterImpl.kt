package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.data.model.ServerStatus
import com.danielealbano.androidremotecontrolmcp.services.mcp.McpServerService
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantServerStarterImpl
    @Inject
    constructor() : AssistantServerStarter {
        override suspend fun ensureRunning(context: Context): Result<Unit> {
            val initial = McpServerService.serverStatus.value
            return if (initial is ServerStatus.Running) {
                Result.success(Unit)
            } else {
                requestStart(context)?.let { Result.failure(AssistantException(it)) } ?: awaitRunning(initial)
            }
        }

        /** Null when the start was issued, else the failure; the service ignores `ACTION_START` while active. */
        private fun requestStart(context: Context): AssistantError? =
            try {
                context.startForegroundService(
                    Intent(context, McpServerService::class.java).apply { action = McpServerService.ACTION_START },
                )
                null
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Logger.w(TAG, "Foreground service start not allowed", e)
                AssistantError.ServerStartBlocked
            }

        /**
         * A stale `Error` already held by the StateFlow (from an earlier failed start) is skipped: the service
         * publishes `Starting` before any new outcome, so the first value that differs from it belongs to this start.
         */
        private suspend fun awaitRunning(initial: ServerStatus): Result<Unit> {
            val stale = initial as? ServerStatus.Error
            val outcome =
                withTimeoutOrNull(START_TIMEOUT_MS) {
                    McpServerService.serverStatus
                        .dropWhile { it == stale }
                        .first { it is ServerStatus.Running || it is ServerStatus.Error }
                }
            return when (outcome) {
                is ServerStatus.Running -> Result.success(Unit)
                is ServerStatus.Error -> failed(outcome.message)
                else -> failed(TIMEOUT_MESSAGE)
            }
        }

        private fun failed(message: String): Result<Unit> {
            val error = AssistantException(AssistantError.ServerStartFailed(message))
            return Result.failure(error)
        }

        private companion object {
            const val TAG = "MCP:AssistantServerStarter"
            const val START_TIMEOUT_MS = 15_000L
            const val TIMEOUT_MESSAGE = "The MCP server did not start within 15 seconds"
        }
    }
