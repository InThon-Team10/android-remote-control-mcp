package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ElderToolFilter.withDefaults")
class ElderToolDefaultsTest {
    private val prefix = McpToolUtils.buildToolNamePrefix("")

    @Test
    fun `screen reads list only what is on screen by default`() {
        val result = ElderToolFilter.withDefaults("${prefix}get_screen_state", JsonObject(emptyMap()), "")

        assertEquals(JsonPrimitive(false), result["include_offscreen"])
    }

    @Test
    fun `an explicit off-screen request is kept`() {
        val args = buildJsonObject { put("include_offscreen", true) }

        assertEquals(args, ElderToolFilter.withDefaults("${prefix}get_screen_state", args, ""))
    }

    @Test
    fun `other tools are left unchanged`() {
        val args = buildJsonObject { put("by", "text") }

        assertEquals(args, ElderToolFilter.withDefaults("${prefix}find_nodes", args, ""))
    }
}
