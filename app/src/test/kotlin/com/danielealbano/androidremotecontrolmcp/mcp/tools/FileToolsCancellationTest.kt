package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.privacy.PrivacyToolGate
import com.danielealbano.androidremotecontrolmcp.services.sharing.EphemeralFileLinkService
import com.danielealbano.androidremotecontrolmcp.services.storage.FileOperationProvider
import com.danielealbano.androidremotecontrolmcp.services.storage.StorageLocationProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("File and sharing tool handlers keep a cancellation a cancellation")
class FileToolsCancellationTest {
    private val provider = mockk<FileOperationProvider>()
    private val args: JsonObject =
        buildJsonObject {
            put("location_id", "loc1")
            put("path", "file.txt")
            put("content", "x")
            put("old_string", "a")
            put("new_string", "b")
        }

    private fun cancelled() = CancellationException("stop")

    @Test
    fun `list_storage_locations rethrows a cancellation`() =
        runTest {
            val locations = mockk<StorageLocationProvider>()
            coEvery { locations.getAllLocations() } throws cancelled()

            val e = assertThrows<CancellationException> { ListStorageLocationsHandler(locations).execute(args) }

            assertEquals("stop", e.message)
        }

    @Test
    fun `list_files rethrows a cancellation`() =
        runTest {
            coEvery { provider.listFiles(any(), any(), any(), any()) } throws cancelled()

            assertThrows<CancellationException> { ListFilesHandler(provider).execute(args) }
        }

    @Test
    fun `read_file rethrows a cancellation`() =
        runTest {
            coEvery { provider.readFile(any(), any(), any(), any()) } throws cancelled()

            assertThrows<CancellationException> { ReadFileHandler(provider).execute(args) }
        }

    @Test
    fun `write_file rethrows a cancellation`() =
        runTest {
            coEvery { provider.writeFile(any(), any(), any()) } throws cancelled()

            assertThrows<CancellationException> { WriteFileHandler(provider).execute(args) }
        }

    @Test
    fun `append_file rethrows a cancellation`() =
        runTest {
            coEvery { provider.appendFile(any(), any(), any()) } throws cancelled()

            assertThrows<CancellationException> { AppendFileHandler(provider).execute(args) }
        }

    @Test
    fun `file_replace rethrows a cancellation`() =
        runTest {
            coEvery { provider.replaceInFile(any(), any(), any(), any(), any()) } throws cancelled()

            assertThrows<CancellationException> { FileReplaceHandler(provider).execute(args) }
        }

    @Test
    fun `delete_file rethrows a cancellation`() =
        runTest {
            coEvery { provider.deleteFile(any(), any()) } throws cancelled()

            assertThrows<CancellationException> { DeleteFileHandler(provider).execute(args) }
        }

    @Test
    fun `share_file_via_web rethrows a cancellation`() =
        runTest {
            coEvery { provider.readFileBytes(any(), any(), any()) } throws cancelled()
            val handler =
                ShareFileViaWebHandler(
                    provider,
                    mockk<EphemeralFileLinkService>(),
                    10,
                    { "http://localhost" },
                    mockk<PrivacyToolGate>(),
                )

            assertThrows<CancellationException> { handler.execute(args) }
        }
}
