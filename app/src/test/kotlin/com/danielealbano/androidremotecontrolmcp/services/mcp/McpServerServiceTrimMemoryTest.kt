package com.danielealbano.androidremotecontrolmcp.services.mcp

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("shouldReleaseCachesOnTrim")
class McpServerServiceTrimMemoryTest {
    // Values of the ComponentCallbacks2.TRIM_MEMORY_* levels.
    private val runningModerate = 5
    private val runningLow = 10
    private val runningCritical = 15
    private val uiHidden = 20
    private val background = 40
    private val moderate = 60
    private val complete = 80

    @Test
    fun `does not release at RUNNING_MODERATE`() {
        assertFalse(shouldReleaseCachesOnTrim(runningModerate))
    }

    @Test
    fun `releases at RUNNING_LOW and RUNNING_CRITICAL`() {
        assertTrue(shouldReleaseCachesOnTrim(runningLow))
        assertTrue(shouldReleaseCachesOnTrim(runningCritical))
    }

    @Test
    fun `does not release at UI_HIDDEN`() {
        assertFalse(shouldReleaseCachesOnTrim(uiHidden))
    }

    @Test
    fun `releases at the background levels`() {
        assertTrue(shouldReleaseCachesOnTrim(background))
        assertTrue(shouldReleaseCachesOnTrim(moderate))
        assertTrue(shouldReleaseCachesOnTrim(complete))
    }

    @Test
    fun `does not release for an unknown level below RUNNING_LOW`() {
        assertFalse(shouldReleaseCachesOnTrim(0))
        assertFalse(shouldReleaseCachesOnTrim(-1))
    }
}
