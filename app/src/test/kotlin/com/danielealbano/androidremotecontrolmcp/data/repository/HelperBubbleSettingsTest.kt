package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("HelperBubbleSettings")
class HelperBubbleSettingsTest {
    @TempDir
    lateinit var tempDir: File

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private lateinit var dataStore: DataStore<Preferences>

    @BeforeEach
    fun setUp() {
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { File(tempDir, "settings.preferences_pb") },
            )
    }

    @Test
    fun `the position is null until the bubble is moved`() =
        testScope.runTest {
            assertNull(DataStoreHelperBubbleSettings(dataStore).helperBubblePosition.first())
        }

    @Test
    fun `the position persists and is clamped to 0 to 1000`() =
        testScope.runTest {
            val settings = DataStoreHelperBubbleSettings(dataStore)

            settings.updateHelperBubblePosition(420)
            assertEquals(420, settings.helperBubblePosition.first())
            settings.updateHelperBubblePosition(1500)
            assertEquals(1000, settings.helperBubblePosition.first())
            settings.updateHelperBubblePosition(-3)
            assertEquals(0, settings.helperBubblePosition.first())
        }

    @Test
    fun `the assistant settings expose the same position`() =
        testScope.runTest {
            val assistant = createAssistantSettings(dataStore, mockk(relaxed = true))

            assistant.updateHelperBubblePosition(250)

            assertEquals(250, assistant.helperBubblePosition.first())
        }
}
