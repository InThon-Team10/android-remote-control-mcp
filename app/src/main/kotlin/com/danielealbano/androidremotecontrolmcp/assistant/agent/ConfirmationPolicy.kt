package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils

/** Gated tools (D10) are matched on the un-prefixed name; a foreign prefix is gated (fail-closed). */
internal object ConfirmationPolicy {
    fun requiresConfirmation(
        toolName: String,
        deviceSlug: String,
    ): Boolean {
        val prefix = McpToolUtils.buildToolNamePrefix(deviceSlug)
        return !toolName.startsWith(prefix) || toolName.removePrefix(prefix) in AssistantConfig.CONFIRMATION_TOOLS
    }
}
