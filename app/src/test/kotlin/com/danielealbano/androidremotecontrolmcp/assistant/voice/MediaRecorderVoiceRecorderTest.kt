package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.content.Context
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.utils.PermissionUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@DisplayName("MediaRecorderVoiceRecorder")
@OptIn(ExperimentalCoroutinesApi::class)
class MediaRecorderVoiceRecorderTest {
    @TempDir
    lateinit var cacheDir: File

    private val context = mockk<Context>()
    private val session = mockk<AudioRecordingSession>(relaxed = true)
    private var factoryCalls = 0
    private var now = 1_000L
    private var onMaxDuration: () -> Unit = {}

    private val assistantDir get() = File(cacheDir, "assistant")

    @BeforeEach
    fun setUp() {
        every { context.cacheDir } returns cacheDir
        mockkObject(PermissionUtils)
        every { PermissionUtils.isMicrophonePermissionGranted(any()) } returns true
        every { session.prepare(any(), any()) } answers {
            firstArg<File>().writeBytes(byteArrayOf(1, 2, 3))
            onMaxDuration = secondArg()
        }
    }

    @AfterEach
    fun tearDown() = unmockkObject(PermissionUtils)

    private fun kotlinx.coroutines.test.TestScope.recorder() =
        MediaRecorderVoiceRecorder(
            context,
            { session.also { factoryCalls++ } },
            { now },
            UnconfinedTestDispatcher(testScheduler),
        )

    private fun Result<*>.error() = (exceptionOrNull() as AssistantException).error

    @Test
    fun `start prepares file in cache assistant dir and starts`() =
        runTest {
            val recorder = recorder()

            assertTrue(recorder.start().isSuccess)

            assertTrue(File(assistantDir, "voice-1.m4a").exists())
            verify { session.start() }
            assertTrue(recorder.isRecording.value)
            assertTrue(recorder.start().isSuccess)
            assertEquals(1, factoryCalls)
        }

    @Test
    fun `start fails with typed error when microphone permission missing`() =
        runTest {
            every { PermissionUtils.isMicrophonePermissionGranted(any()) } returns false
            val recorder = recorder()

            assertEquals(AssistantError.MicrophonePermissionMissing, recorder.start().error())
            assertEquals(0, factoryCalls)
            assertFalse(recorder.isRecording.value)
        }

    @Test
    fun `start failure releases session deletes file and a later start succeeds`() =
        runTest {
            every { session.start() } throws IllegalStateException("boom") andThen Unit
            val recorder = recorder()

            assertEquals(AssistantError.VoiceRecordingFailed, recorder.start().error())
            verify(exactly = 1) { session.release() }
            assertFalse(recorder.isRecording.value)
            assertTrue(assistantDir.listFiles().orEmpty().isEmpty())

            assertTrue(recorder.start().isSuccess)
            assertTrue(File(assistantDir, "voice-2.m4a").exists())
        }

    @Test
    fun `start failure when factory throws or cache dir cannot be created`() =
        runTest {
            val throwing =
                MediaRecorderVoiceRecorder(
                    context,
                    { throw IllegalStateException("no recorder") },
                    { now },
                    UnconfinedTestDispatcher(testScheduler),
                )
            assertEquals(AssistantError.VoiceRecordingFailed, throwing.start().error())

            File(cacheDir, "assistant").writeText("a file blocks the directory")
            val blocked = recorder()
            assertEquals(AssistantError.VoiceRecordingFailed, blocked.start().error())
            assertEquals(0, factoryCalls)
        }

    @Test
    fun `start purges stale files`() =
        runTest {
            assistantDir.mkdirs()
            val stale = File(assistantDir, "voice-9.m4a").apply { writeBytes(byteArrayOf(9)) }

            recorder().start()

            assertFalse(stale.exists())
        }

    @Test
    fun `stop returns bytes and duration then deletes file and releases once`() =
        runTest {
            val recorder = recorder()
            recorder.start()
            now += 1_500

            val clip = recorder.stop().getOrThrow()

            assertArrayEquals(byteArrayOf(1, 2, 3), clip.bytes)
            assertEquals(1_500L, clip.durationMs)
            assertFalse(File(assistantDir, "voice-1.m4a").exists())
            assertFalse(recorder.isRecording.value)
            verify(exactly = 1) { session.release() }
        }

    @Test
    fun `stop caps duration at 60 seconds`() =
        runTest {
            val recorder = recorder()
            recorder.start()
            now += 90_000

            assertEquals(60_000L, recorder.stop().getOrThrow().durationMs)
        }

    @Test
    fun `stop when not recording fails`() =
        runTest {
            assertEquals(AssistantError.VoiceRecordingFailed, recorder().stop().error())
        }

    @Test
    fun `stop failure on session error or empty file releases and deletes`() =
        runTest {
            every { session.stop() } throws IllegalStateException("boom") andThen Unit
            val recorder = recorder()
            recorder.start()
            assertTrue(recorder.stop().isFailure)
            assertFalse(recorder.isRecording.value)
            assertFalse(File(assistantDir, "voice-1.m4a").exists())

            every { session.prepare(any(), any()) } answers { firstArg<File>().writeBytes(ByteArray(0)) }
            recorder.start()
            assertTrue(recorder.stop().isFailure)
            assertFalse(File(assistantDir, "voice-2.m4a").exists())
            verify(exactly = 2) { session.release() }
        }

    @Test
    fun `max duration callback sets flag and stop still returns clip`() =
        runTest {
            val recorder = recorder()
            recorder.start()

            onMaxDuration()
            assertTrue(recorder.maxDurationReached.value)

            assertTrue(recorder.stop().isSuccess)
            assertFalse(recorder.maxDurationReached.value)
        }

    @Test
    fun `cancel releases and deletes file and is a no-op when idle`() =
        runTest {
            val recorder = recorder()
            recorder.cancel()
            assertEquals(0, factoryCalls)

            recorder.start()
            recorder.cancel()

            assertFalse(recorder.isRecording.value)
            assertFalse(File(assistantDir, "voice-1.m4a").exists())
            verify(exactly = 1) { session.release() }
        }
}
