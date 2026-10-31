package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [VoiceRecorder] over a platform [AudioRecordingSession]; files live in `cacheDir/assistant/voice-<n>.m4a`. */
@Singleton
class MediaRecorderVoiceRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionFactory: AudioRecordingSessionFactory,
        private val clock: ElapsedClock,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) : VoiceRecorder {
        private class ActiveRecording(
            val session: AudioRecordingSession,
            val file: File,
            val startedAtMs: Long,
        )

        private val mutex = Mutex()
        private val _isRecording = MutableStateFlow(false)
        private val _maxDurationReached = MutableStateFlow(false)
        private var active: ActiveRecording? = null
        private var counter = 0L

        override val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
        override val maxDurationReached: StateFlow<Boolean> = _maxDurationReached.asStateFlow()

        override suspend fun start(): Result<Unit> =
            withContext(io) {
                mutex.withLock {
                    when {
                        active != null -> {
                            Result.success(Unit)
                        }

                        !PermissionUtils.isMicrophonePermissionGranted(context) -> {
                            Result.failure(AssistantException(AssistantError.MicrophonePermissionMissing))
                        }

                        else -> {
                            open()
                        }
                    }
                }
            }

        override suspend fun stop(): Result<VoiceClip> =
            withContext(io) {
                mutex.withLock {
                    val recording = active
                    if (recording == null) {
                        failure()
                    } else {
                        runCatching { finish(recording) }
                            .also { discard(recording) }
                            .onFailure { Logger.w(TAG, "Voice recording failed to finish: ${it.javaClass.simpleName}") }
                            .fold({ Result.success(it) }, { failure() })
                    }
                }
            }

        override suspend fun cancel() {
            withContext(io + NonCancellable) { mutex.withLock { active?.let { discard(it) } } }
        }

        private fun open(): Result<Unit> {
            val dir = File(context.cacheDir, DIR_NAME)
            val file = File(dir, "voice-${++counter}.m4a")
            var session: AudioRecordingSession? = null
            return runCatching {
                check(dir.isDirectory || dir.mkdirs()) { "Cannot create voice cache directory" }
                dir.listFiles()?.forEach { it.delete() }
                val created = sessionFactory.create().also { session = it }
                created.prepare(file) { _maxDurationReached.value = true }
                _maxDurationReached.value = false
                created.start()
                active = ActiveRecording(created, file, clock.elapsedRealtimeMs())
                _isRecording.value = true
            }.onFailure {
                Logger.w(TAG, "Voice recording failed to start: ${it.javaClass.simpleName}")
                session?.release()
                file.delete()
            }.fold({ Result.success(Unit) }, { failure() })
        }

        private fun <T> failure(): Result<T> = Result.failure(AssistantException(AssistantError.VoiceRecordingFailed))

        private fun finish(recording: ActiveRecording): VoiceClip {
            recording.session.stop()
            val durationMs =
                (clock.elapsedRealtimeMs() - recording.startedAtMs)
                    .coerceIn(0L, AndroidAudioRecordingSession.MAX_DURATION_MS.toLong())
            val bytes = recording.file.readBytes()
            check(bytes.isNotEmpty()) { "Empty recording" }
            return VoiceClip(bytes, durationMs)
        }

        private fun discard(recording: ActiveRecording) {
            active = null
            _isRecording.value = false
            _maxDurationReached.value = false
            recording.session.release()
            recording.file.delete()
        }

        private companion object {
            const val TAG = "MCP:AssistantVoice"
            const val DIR_NAME = "assistant"
        }
    }
