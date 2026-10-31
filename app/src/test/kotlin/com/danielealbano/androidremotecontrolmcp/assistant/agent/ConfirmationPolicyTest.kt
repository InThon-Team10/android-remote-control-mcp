package com.danielealbano.androidremotecontrolmcp.assistant.agent

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ConfirmationPolicyTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "android_close_app",
            "android_share_file_via_web",
            "android_take_camera_photo",
            "android_save_camera_photo",
            "android_save_camera_video",
            "android_set_clipboard",
        ],
    )
    fun `gated tools require confirmation without slug`(name: String) {
        assertTrue(ConfirmationPolicy.requiresConfirmation(name, ""))
    }

    @Test
    fun `gated tool with slug requires confirmation`() {
        assertTrue(ConfirmationPolicy.requiresConfirmation("android_pixel7_close_app", "pixel7"))
    }

    @Test
    fun `other tools and look-alike names do not require confirmation`() {
        assertFalse(ConfirmationPolicy.requiresConfirmation("android_tap", ""))
        assertFalse(ConfirmationPolicy.requiresConfirmation("android_get_screen_state", ""))
        assertFalse(ConfirmationPolicy.requiresConfirmation("android_pixel7_close_app_extra", "pixel7"))
    }

    @Test
    fun `foreign prefix is gated fail-closed`() {
        assertTrue(ConfirmationPolicy.requiresConfirmation("android_other_tap", "pixel7"))
    }
}
