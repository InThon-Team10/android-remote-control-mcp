package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.media.AudioDeviceInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("pickCommunicationDevice")
class LiveAudioRouteTest {
    @Test
    fun `picks the loudspeaker when only built-in devices exist`() {
        val types = listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)

        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, pickCommunicationDevice(types))
    }

    @Test
    fun `a connected headset wins over the loudspeaker`() {
        val types =
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
            )

        assertEquals(AudioDeviceInfo.TYPE_WIRED_HEADSET, pickCommunicationDevice(types))
    }

    @Test
    fun `a bluetooth device wins over the loudspeaker`() {
        val types = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BLUETOOTH_SCO)

        assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, pickCommunicationDevice(types))
    }

    @Test
    fun `never picks the earpiece`() {
        assertNull(pickCommunicationDevice(listOf(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)))
    }
}
