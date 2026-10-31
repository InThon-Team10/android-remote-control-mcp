package com.danielealbano.androidremotecontrolmcp.mcp.tools

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("CompositeToolCallIndicator")
class CompositeToolCallIndicatorTest {
    private val visual = mockk<ToolCallIndicator>(relaxed = true)
    private val activity = mockk<ToolCallIndicator>(relaxed = true)
    private val composite = CompositeToolCallIndicator(visual, activity)

    @Test
    fun `started reaches both targets`() {
        composite.onToolCallStarted("tap")
        verify { visual.onToolCallStarted("tap") }
        verify { activity.onToolCallStarted("tap") }
    }

    @Test
    fun `finished reaches both targets`() {
        composite.onToolCallFinished("tap")
        verify { visual.onToolCallFinished("tap") }
        verify { activity.onToolCallFinished("tap") }
    }

    @Test
    fun `setEnabled reaches only the visual target`() {
        composite.setEnabled(false)
        verify { visual.setEnabled(false) }
        verify(exactly = 0) { activity.setEnabled(any()) }
    }

    @Test
    fun `failing visual target does not block activity`() {
        every { visual.onToolCallStarted(any()) } throws IllegalStateException("boom")
        composite.onToolCallStarted("tap")
        verify { activity.onToolCallStarted("tap") }
    }

    @Test
    fun `failing activity target does not block visual`() {
        every { activity.onToolCallStarted(any()) } throws IllegalStateException("boom")
        composite.onToolCallStarted("tap")
        verify { visual.onToolCallStarted("tap") }
    }
}
