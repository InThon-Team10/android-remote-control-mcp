package com.danielealbano.androidremotecontrolmcp.assistant.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.JsonObject

/** The assistant's MCP client session towards this app's own server. All failures are `AssistantException`s. */
interface AssistantMcpConnection {
    /** Connects (idempotent; reconnects after close or failure). */
    suspend fun connect(): Result<Unit>

    suspend fun listTools(): Result<List<Tool>>

    suspend fun callTool(
        name: String,
        arguments: JsonObject,
    ): Result<CallToolResult>

    /** Drops the session; the next call reconnects. A run is stopped by cancelling the caller, not by this. */
    suspend fun close()
}
