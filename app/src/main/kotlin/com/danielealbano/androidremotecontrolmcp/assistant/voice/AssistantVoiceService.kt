package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.danielealbano.androidremotecontrolmcp.R
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Foreground (microphone) service alive for the duration of a live voice conversation; "End" stops it. */
@AndroidEntryPoint
class AssistantVoiceService : Service() {
    @Inject
    lateinit var liveVoice: LiveVoiceSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.assistant_voice_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        Log.d(VOICE_TRACE, "voice service start command action=${intent?.action ?: "start"}")
        if (intent?.action == ACTION_END) scope.launch { liveVoice.stop() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(VOICE_TRACE, "voice service destroyed")
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val end =
            PendingIntent.getService(
                this,
                0,
                Intent(this, AssistantVoiceService::class.java).setAction(ACTION_END),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.assistant_voice_listening))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.assistant_voice_end), end).build())
            .build()
    }

    companion object {
        const val ACTION_END = "com.danielealbano.androidremotecontrolmcp.assistant.voice.END"
        private const val CHANNEL_ID = "assistant_voice"
        private const val NOTIFICATION_ID = 4711
    }
}

/** Starts the service from the visible UI context (required for microphone foreground services). */
class AndroidVoiceServiceLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : VoiceServiceLauncher {
        override fun start() {
            ContextCompat.startForegroundService(context, Intent(context, AssistantVoiceService::class.java))
        }

        override fun stop() {
            context.stopService(Intent(context, AssistantVoiceService::class.java))
        }
    }
