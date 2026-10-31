package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.storage.FileOperationProvider
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Write tool handlers reject a path naming a directory")
class FileToolsTrailingSlashTest {
    private val provider = mockk<FileOperationProvider>(relaxed = true)

    private fun args(path: String): JsonObject =
        buildJsonObject {
            put("location_id", "loc1")
            put("path", path)
            put("content", "x")
            put("old_string", "a")
            put("new_string", "b")
        }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        val e = assertThrows<McpToolException.InvalidParams> { block() }
        assertEquals("Parameter 'path' must name a file, not a directory", e.message)
    }

    @Test
    fun `write_file rejects a trailing slash`() =
        runTest {
            assertRejected { WriteFileHandler(provider).execute(args("dir/")) }

            coVerify(exactly = 0) { provider.writeFile(any(), any(), any()) }
        }

    @Test
    fun `append_file rejects a trailing slash`() =
        runTest {
            assertRejected { AppendFileHandler(provider).execute(args("a/b/")) }

            coVerify(exactly = 0) { provider.appendFile(any(), any(), any()) }
        }

    @Test
    fun `file_replace rejects a trailing slash`() =
        runTest {
            assertRejected { FileReplaceHandler(provider).execute(args("dir/")) }

            coVerify(exactly = 0) { provider.replaceInFile(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `write_file still accepts a path without a trailing slash`() =
        runTest {
            WriteFileHandler(provider).execute(args("dir/file.txt"))

            coVerify(exactly = 1) { provider.writeFile("loc1", "dir/file.txt", "x") }
        }

    @Test
    fun `delete_file is not affected by a trailing slash`() =
        runTest {
            DeleteFileHandler(provider).execute(args("dir/"))

            coVerify(exactly = 1) { provider.deleteFile("loc1", "dir/") }
        }
}
