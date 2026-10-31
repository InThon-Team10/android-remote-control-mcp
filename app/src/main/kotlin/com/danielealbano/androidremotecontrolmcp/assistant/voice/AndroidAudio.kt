package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.core.content.ContextCompat
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

/** `AudioRecord` microphone (VOICE_COMMUNICATION, with the platform echo canceller when available). */
class AndroidAudioCapture
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : AudioCapture {
        override fun hasPermission(): Boolean = PermissionUtils.isMicrophonePermissionGranted(context)

        override fun chunks(): Flow<ByteArray> =
            flow {
                val record = createRecord()
                var echo: AcousticEchoCanceler? = null
                try {
                    check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialised" }
                    if (AcousticEchoCanceler.isAvailable()) {
                        echo = AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
                    }
                    record.startRecording()
                    val buffer = ByteArray(LiveAudio.CHUNK_BYTES)
                    while (currentCoroutineContext().isActive) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read < 0) throw IOException("AudioRecord read failed: $read")
                        if (read > 0) emit(buffer.copyOf(read))
                    }
                } finally {
                    echo?.release()
                    runCatching { record.stop() }
                    record.release()
                }
            }.flowOn(dispatcher)

        private fun createRecord(): AudioRecord {
            val granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            if (!granted) throw SecurityException("RECORD_AUDIO not granted")
            val minimum = AudioRecord.getMinBufferSize(LiveAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING)
            return AudioRecord
                .Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(format(LiveAudio.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO))
                .setBufferSizeInBytes(maxOf(minimum, LiveAudio.CHUNK_BYTES * 2))
                .build()
        }
    }

/** `AudioTrack` speaker fed by a single writer thread; [flush] invalidates everything queued before it. */
class AndroidAudioPlayback
    @Inject
    constructor(
        private val route: LiveAudioRoute,
    ) : AudioPlayback {
        private val lock = Any()
        private var track: AudioTrack? = null
        private var executor: ExecutorService? = null
        private var generation = 0
        private var writtenFrames = 0L
        private var headBase = 0L

        override fun start() {
            synchronized(lock) {
                if (track != null) return
                route.acquire()
                val minimum = AudioTrack.getMinBufferSize(LiveAudio.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING)
                track =
                    AudioTrack
                        .Builder()
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build(),
                        ).setAudioFormat(format(LiveAudio.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO))
                        .setBufferSizeInBytes(minimum * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                        .also { it.play() }
                executor = Executors.newSingleThreadExecutor()
            }
        }

        override fun write(pcm: ByteArray) {
            val queued =
                synchronized(lock) {
                    writtenFrames += pcm.size / FRAME_BYTES
                    generation
                }
            executor?.execute {
                val current = synchronized(lock) { track?.takeIf { queued == generation } } ?: return@execute
                current.write(pcm, 0, pcm.size)
            }
        }

        override fun flush() {
            Log.d(VOICE_TRACE, "playback flushed (interrupted)")
            synchronized(lock) {
                generation++
                track?.apply {
                    pause()
                    flush()
                    play()
                }
                writtenFrames = 0
                headBase = track?.playbackHeadPosition?.toLong() ?: 0L
            }
        }

        override fun pendingMs(): Long =
            synchronized(lock) {
                val played = (track?.playbackHeadPosition?.toLong() ?: 0L) - headBase
                (writtenFrames - played).coerceAtLeast(0) * MS_PER_SECOND / LiveAudio.SAMPLE_RATE
            }

        override fun release() {
            synchronized(lock) {
                generation++
                writtenFrames = 0
                headBase = 0
                executor?.shutdownNow()
                executor = null
                track?.release()
                track = null
                route.release()
            }
        }
    }

private const val FRAME_BYTES = 2
private const val MS_PER_SECOND = 1000L
private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

private fun format(
    rate: Int,
    channelMask: Int,
): AudioFormat =
    AudioFormat
        .Builder()
        .setEncoding(ENCODING)
        .setSampleRate(rate)
        .setChannelMask(channelMask)
        .build()
