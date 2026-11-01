package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import android.content.Context

/** Makes sure the MCP server is running, starting it when needed. Failures are `AssistantException`s. */
interface AssistantServerStarter {
    /** [context] must belong to a visible UI component so the foreground-service start is allowed. */
    suspend fun ensureRunning(context: Context): Result<Unit>
}
