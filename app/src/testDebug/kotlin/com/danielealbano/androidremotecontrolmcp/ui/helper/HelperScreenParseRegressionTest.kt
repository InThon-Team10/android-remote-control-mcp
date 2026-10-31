package com.danielealbano.androidremotecontrolmcp.ui.helper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelperScreenParseRegressionTest {
    private fun screen(
        header: String,
        bounds: String = "10,20,200,80",
    ) = "screen:720x1600 density:280 orientation:portrait\n" +
        header + "\n" +
        "node_id\tclass\ttext\tdesc\tres_id\tbounds\tflags\n" +
        "1:1\tButton\tSearch\t-\t-\t$bounds\ton,clk,ena"

    @Test
    fun `window title text cannot turn an overlay window into an application window`() {
        val header = "--- window:2 type:ACCESSIBILITY_OVERLAY pkg:evil title:x type:APPLICATION  y layer:5 focused:false ---"
        assertTrue(parseHelperScreen(screen(header)).nodes.isEmpty())
    }

    @Test
    fun `package text cannot turn a system window into an application window`() {
        val header = "--- window:2 type:SYSTEM pkg:type:APPLICATION  title:t layer:5 focused:false ---"
        assertTrue(parseHelperScreen(screen(header)).nodes.isEmpty())
    }

    @Test
    fun `real application window header is still accepted`() {
        val header = "--- window:-1 type:APPLICATION pkg:p title:t layer:0 focused:true ---"
        assertEquals(setOf("1:1"), parseHelperScreen(screen(header)).nodes.keys)
    }

    @Test
    fun `malformed bounds columns are rejected instead of shifted or coerced`() {
        val header = "--- window:1 type:APPLICATION pkg:p title:t layer:0 focused:true ---"
        listOf(
            "x,10,20,200,80",
            "10,20,200",
            "10,20,200,80,5",
            "10,,20,200,80",
            "1,1,200,1e2",
            "10f,20,200,80",
            "0x10,20,200,80",
            "10, 20,200,80",
        ).forEach { bounds ->
            assertTrue(parseHelperScreen(screen(header, bounds)).nodes.isEmpty(), bounds)
        }
        assertEquals(1, parseHelperScreen(screen(header, "10,20,200,80")).nodes.size)
    }
}
