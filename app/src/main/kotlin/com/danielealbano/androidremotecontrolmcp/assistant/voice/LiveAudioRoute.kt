package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes the live conversation like a speakerphone call: voice-communication mode, so the platform echo canceller
 * keeps removing the reply from the microphone, on a connected headset or Bluetooth device, otherwise on the
 * loudspeaker. Without it, voice-communication audio plays from the earpiece and the user hears nothing unless the
 * phone is held to the ear. [release] restores the previous mode; both calls are idempotent.
 */
@Singleton
class LiveAudioRoute
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val audio: AudioManager = context.getSystemService(AudioManager::class.java)
        private val lock = Any()
        private var previousMode: Int? = null

        fun acquire() {
            synchronized(lock) {
                if (previousMode != null) return
                previousMode = audio.mode
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                val devices = audio.availableCommunicationDevices
                val type = pickCommunicationDevice(devices.map { it.type })
                val device = devices.firstOrNull { it.type == type }
                val routed = device != null && audio.setCommunicationDevice(device)
                Log.d(VOICE_TRACE, "live audio route: type=$type routed=$routed")
            }
        }

        fun release() {
            synchronized(lock) {
                val mode = previousMode ?: return
                previousMode = null
                audio.clearCommunicationDevice()
                audio.mode = mode
            }
        }
    }

/** A connected headset, Bluetooth or hearing-aid device wins; otherwise the loudspeaker; null when neither exists. */
internal fun pickCommunicationDevice(types: List<Int>): Int? =
    types.firstOrNull { it in PERSONAL_DEVICE_TYPES }
        ?: types.firstOrNull { it == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

private val PERSONAL_DEVICE_TYPES =
    setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )
