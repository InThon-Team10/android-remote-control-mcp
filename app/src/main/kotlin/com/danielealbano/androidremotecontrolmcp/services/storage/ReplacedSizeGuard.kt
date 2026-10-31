package com.danielealbano.androidremotecontrolmcp.services.storage

import com.danielealbano.androidremotecontrolmcp.data.model.ServerConfig
import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException

private const val BYTES_PER_MB = 1024L * 1024L

/**
 * Throws [McpToolException.ActionFailed] if replacing [replacementCount] occurrences of [oldString] with [newString]
 * would grow a file of [fileSize] bytes past the configured limit. Runs before the replaced content is built so an
 * oversize result is never allocated.
 */
internal fun requireReplacedSizeWithinLimit(
    fileSize: Long,
    replacementCount: Int,
    oldString: String,
    newString: String,
    config: ServerConfig,
) {
    val limitBytes = config.fileSizeLimitMb.toLong() * BYTES_PER_MB
    val growthPerReplacement =
        newString.toByteArray(Charsets.UTF_8).size - oldString.toByteArray(Charsets.UTF_8).size
    if (fileSize + replacementCount.toLong() * growthPerReplacement > limitBytes) {
        throw McpToolException.ActionFailed(
            "Replacing would exceed the configured file size limit of ${config.fileSizeLimitMb} MB.",
        )
    }
}
