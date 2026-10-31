package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.services.apps.AppManager
import com.danielealbano.androidremotecontrolmcp.services.camera.CameraProvider
import com.danielealbano.androidremotecontrolmcp.services.camera.VideoRecordingResult
import com.danielealbano.androidremotecontrolmcp.services.intents.IntentDispatcher
import com.danielealbano.androidremotecontrolmcp.services.storage.FileOperationProvider
import io.mockk.coEvery
import io.mockk.mockk
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Success texts escape echoed client-supplied values")
class EchoedValueEscapingTest {
    private val backslash = Char(BACKSLASH_CODE)
    private val hostile = "a" + Char(0x85) + "b" + Char(0x2028) + "c" + Char(0x2029) + "d" + Char(0x9F) + "e"
    private val escaped =
        "a" + backslash + "u0085b" + backslash + "u2028c" + backslash + "u2029d" + backslash + "u009fe"
    private val provider = mockk<FileOperationProvider>(relaxed = true)
    private val appManager = mockk<AppManager>(relaxed = true)
    private val cameraProvider = mockk<CameraProvider>(relaxed = true)
    private val intentDispatcher = mockk<IntentDispatcher>(relaxed = true)

    private fun fileArgs(): JsonObject =
        buildJsonObject {
            put("location_id", hostile)
            put("path", hostile)
            put("content", "x")
            put("url", "https://example.com/f")
        }

    private fun text(result: CallToolResult): String = (result.content[0] as TextContent).text

    private fun assertEscaped(
        text: String,
        singleLine: Boolean = true,
    ) {
        assertFalse(text.any { (it.isISOControl() && it != Char(10)) || it == Char(0x2028) || it == Char(0x2029) }, text)
        if (singleLine) assertFalse(text.contains(Char(10)), text)
        assertTrue(text.contains(escaped), text)
    }

    @Test
    fun `write_file escapes location and path`() =
        runTest {
            assertEscaped(text(WriteFileHandler(provider).execute(fileArgs())))
        }

    @Test
    fun `append_file escapes location and path`() =
        runTest {
            assertEscaped(text(AppendFileHandler(provider).execute(fileArgs())))
        }

    @Test
    fun `delete_file escapes location and path`() =
        runTest {
            assertEscaped(text(DeleteFileHandler(provider).execute(fileArgs())))
        }

    @Test
    fun `download_from_url escapes location and path`() =
        runTest {
            coEvery { provider.downloadFromUrl(any(), any(), any()) } returns 3L
            assertEscaped(text(DownloadFromUrlHandler(provider).execute(fileArgs())))
        }

    @Test
    fun `open_app escapes package id`() =
        runTest {
            coEvery { appManager.openApp(any()) } returns Result.success(Unit)
            val args = buildJsonObject { put("package_id", hostile) }
            assertEscaped(text(OpenAppHandler(appManager).execute(args)))
        }

    @Test
    fun `close_app escapes package id`() =
        runTest {
            coEvery { appManager.closeApp(any()) } returns Result.success(Unit)
            val args = buildJsonObject { put("package_id", hostile) }
            assertEscaped(text(CloseAppHandler(appManager).execute(args)))
        }

    @Test
    fun `save_camera_photo escapes path`() =
        runTest {
            coEvery { cameraProvider.savePhoto(any(), any(), any(), any(), any(), any()) } returns 5L
            val args =
                buildJsonObject {
                    put("camera_id", "0")
                    put("location_id", "loc1")
                    put("path", hostile)
                }
            assertEscaped(text(SaveCameraPhotoHandler(cameraProvider, provider).execute(args)))
        }

    @Test
    fun `save_camera_video escapes path`() =
        runTest {
            coEvery { cameraProvider.saveVideo(any(), any(), any(), any(), any(), any(), any()) } returns
                VideoRecordingResult(5L, 1000L, "thumb", 1, 1)
            val args =
                buildJsonObject {
                    put("camera_id", "0")
                    put("location_id", "loc1")
                    put("path", hostile)
                    put("duration", 1)
                }
            assertEscaped(text(SaveCameraVideoHandler(cameraProvider, provider).execute(args)), singleLine = false)
        }

    @Test
    fun `open_uri escapes uri`() =
        runTest {
            coEvery { intentDispatcher.openUri(any(), any(), any()) } returns Result.success(Unit)
            val args = buildJsonObject { put("uri", hostile) }
            assertEscaped(text(OpenUriHandler(intentDispatcher).execute(args)))
        }

    @Test
    fun `send_intent escapes action`() =
        runTest {
            coEvery { intentDispatcher.sendIntent(any()) } returns Result.success(Unit)
            val args =
                buildJsonObject {
                    put("type", "activity")
                    put("action", hostile)
                }
            assertEscaped(text(SendIntentHandler(intentDispatcher).execute(args)))
        }

    @Test
    fun `normal values are echoed unchanged`() =
        runTest {
            val args =
                buildJsonObject {
                    put("location_id", "loc1")
                    put("path", "dir/f é.txt")
                    put("content", "x")
                }
            assertEquals("File written successfully: loc1/dir/f é.txt", text(WriteFileHandler(provider).execute(args)))
        }

    private companion object {
        const val BACKSLASH_CODE = 92
    }
}
