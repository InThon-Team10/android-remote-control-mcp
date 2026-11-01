package com.danielealbano.androidremotecontrolmcp.services.mcp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.services.channel.EventChannelService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * Receives BOOT_COMPLETED broadcast and starts the MCP server when the auto-start setting is enabled or the
 * helper is on (the helper's assistant needs the server; see [shouldStartMcpServerOnBoot]).
 *
 * Uses [goAsync] to extend the broadcast receiver's lifecycle beyond the
 * default 10-second limit, allowing a coroutine to read settings from DataStore.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.i(TAG, "Boot completed broadcast received")

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(SETTINGS_READ_TIMEOUT_MS) {
                    val config = settingsRepository.serverConfig.first()
                    val helperEnabled = settingsRepository.getConfig().enabled

                    if (shouldStartMcpServerOnBoot(config.autoStartOnBoot, helperEnabled)) {
                        Log.i(
                            TAG,
                            "Starting McpServerService on boot (autoStart=${config.autoStartOnBoot}, " +
                                "helper=$helperEnabled)",
                        )
                        val serviceIntent =
                            Intent(context, McpServerService::class.java).apply {
                                action = McpServerService.ACTION_START
                            }
                        context.startForegroundService(serviceIntent)
                    } else {
                        Log.i(TAG, "Auto-start and helper disabled, skipping MCP server start")
                    }

                    // Event Channel auto-start
                    val channelConfig = settingsRepository.getEventChannelConfig()
                    if (channelConfig.enabled && channelConfig.endpointUrl.isNotBlank()) {
                        val channelIntent =
                            Intent(context, EventChannelService::class.java).apply {
                                action = EventChannelService.ACTION_START
                            }
                        context.startForegroundService(channelIntent)
                        Log.i(TAG, "Event channel auto-started on boot")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking auto-start setting on boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MCP:BootReceiver"
        private const val SETTINGS_READ_TIMEOUT_MS = 10_000L
    }
}

/**
 * The MCP server starts at boot when the auto-start setting is on, or when the elder helper is on: the helper's
 * bubble comes back after a reboot (Android rebinds the accessibility service) and its assistant needs the server.
 */
internal fun shouldStartMcpServerOnBoot(
    autoStartOnBoot: Boolean,
    helperEnabled: Boolean,
): Boolean = autoStartOnBoot || helperEnabled
