package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.content.Context
import android.media.MediaRecorder
import java.io.File

/** Platform glue around [MediaRecorder] (MPEG_4/AAC, 16 kHz, mono, 32 kbps, 60 s max). */
class AndroidAudioRecordingSession(
    context: Context,
) : AudioRecordingSession {
    private val recorder = MediaRecorder(context)

    @Volatile
    private var autoStopped = false

    override fun prepare(
        file: File,
        onMaxDuration: () -> Unit,
    ) {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(SAMPLE_RATE_HZ)
        recorder.setAudioChannels(1)
        recorder.setAudioEncodingBitRate(BIT_RATE_BPS)
        recorder.setMaxDuration(MAX_DURATION_MS)
        recorder.setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                autoStopped = true
                onMaxDuration()
            }
        }
        recorder.setOutputFile(file)
        recorder.prepare()
    }

    override fun start() = recorder.start()

    override fun stop() {
        runCatching { recorder.stop() }.onFailure { if (!autoStopped) throw it }
    }

    override fun release() {
        runCatching { recorder.release() }
    }

    companion object {
        const val MAX_DURATION_MS = 60_000
        private const val SAMPLE_RATE_HZ = 16_000
        private const val BIT_RATE_BPS = 32_000
    }
}
