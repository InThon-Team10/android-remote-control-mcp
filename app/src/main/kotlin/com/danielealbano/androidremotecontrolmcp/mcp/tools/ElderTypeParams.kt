package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.privacy.PlaceholderSubstitutor
import kotlinx.serialization.json.JsonObject

/**
 * Validates the parameters of the `type_*` tools exactly like the tools do, without ever keeping or echoing the text
 * (it is only measured; under guidance the user types it).
 */
internal class ElderTypeParams(
    private val substitutor: PlaceholderSubstitutor,
) {
    fun validate(
        toolName: String,
        arguments: JsonObject?,
    ) {
        when (toolName) {
            "type_append_text" -> {
                requireText(arguments, "text")
            }

            "type_insert_text" -> {
                requireText(arguments, "text")
                val offset = McpToolUtils.requireInt(arguments, "offset")
                if (offset < 0) throw McpToolException.InvalidParams("Parameter 'offset' must be >= 0, got $offset")
            }

            "type_replace_text" -> {
                val search = substitutor.substitute(McpToolUtils.requireString(arguments, "search"))
                if (search.isEmpty()) throw McpToolException.InvalidParams("Parameter 'search' must be non-empty")
                val newText = substitutor.substitute(McpToolUtils.requireString(arguments, "new_text"))
                if (newText.isNotEmpty()) validateTextLength(newText, "new_text")
            }
        }
        if (toolName != "type_clear_text") extractTypingParams(arguments)
    }

    private fun requireText(
        arguments: JsonObject?,
        name: String,
    ) {
        val text = substitutor.substitute(McpToolUtils.requireString(arguments, name))
        if (text.isEmpty()) throw McpToolException.InvalidParams("Parameter '$name' must be non-empty")
        validateTextLength(text, name)
    }
}
