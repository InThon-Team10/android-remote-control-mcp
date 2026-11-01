package com.danielealbano.androidremotecontrolmcp.services.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("shouldStartMcpServerOnBoot")
class BootCompletedReceiverTest {
    @ParameterizedTest(name = "autoStart={0}, helper={1} -> {2}")
    @CsvSource(
        "false, false, false",
        "true, false, true",
        "false, true, true",
        "true, true, true",
    )
    fun `starts the server when auto-start or the helper is on`(
        autoStartOnBoot: Boolean,
        helperEnabled: Boolean,
        expected: Boolean,
    ) {
        assertEquals(expected, shouldStartMcpServerOnBoot(autoStartOnBoot, helperEnabled))
    }
}
