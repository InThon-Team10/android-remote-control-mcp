package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import com.danielealbano.androidremotecontrolmcp.data.model.BindingAddress
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GeneralSettingsScreen")
class GeneralSettingsScreenTest {
    @Test
    fun `network and a specific IP ask for the exposure warning, localhost does not`() {
        assertTrue(bindingNeedsExposureWarning(BindingAddress.NETWORK))
        assertTrue(bindingNeedsExposureWarning(BindingAddress.SPECIFIC))
        assertFalse(bindingNeedsExposureWarning(BindingAddress.LOCALHOST))
    }
}
