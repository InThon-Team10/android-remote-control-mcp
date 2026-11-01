package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("AssistantProviderMigration")
class AssistantProviderMigrationTest {
    @TempDir
    lateinit var tempDir: File

    private val testScope = TestScope(UnconfinedTestDispatcher())

    @Test
    fun `old Luna planner selection migrates to Terra without losing key or consent settings`() =
        testScope.runTest {
            val before =
                preferencesOf(
                    ASSISTANT_MODEL_KEY to "gpt-6-luna",
                    ASSISTANT_API_KEY_CIPHERTEXT_KEY to "openai-ciphertext",
                    ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY to true,
                )
            assertTrue(AssistantProviderMigration.shouldMigrate(before))
            val after = AssistantProviderMigration.migrate(before)
            assertNull(after[ASSISTANT_MODEL_KEY])
            assertEquals("gpt-6.1-terra", AssistantConfig.DEFAULT_MODEL)
            assertEquals("openai-ciphertext", after[ASSISTANT_API_KEY_CIPHERTEXT_KEY])
            assertEquals(true, after[ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY])
        }

    @Test
    fun `drops the key saved for the previous provider and keeps everything else`() =
        testScope.runTest {
            val before =
                preferencesOf(
                    LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY to "previous-ciphertext",
                    ASSISTANT_API_KEY_CIPHERTEXT_KEY to "openai-ciphertext",
                    ASSISTANT_ENABLED_KEY to true,
                    ASSISTANT_MODEL_KEY to AssistantConfig.DEFAULT_MODEL,
                )

            assertTrue(AssistantProviderMigration.shouldMigrate(before))
            val after = AssistantProviderMigration.migrate(before)

            assertFalse(LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY in after)
            assertEquals("openai-ciphertext", after[ASSISTANT_API_KEY_CIPHERTEXT_KEY])
            assertEquals(true, after[ASSISTANT_ENABLED_KEY])
            assertEquals(AssistantConfig.DEFAULT_MODEL, after[ASSISTANT_MODEL_KEY])
        }

    @Test
    fun `drops a stored model of the previous provider`() =
        testScope.runTest {
            val before = preferencesOf(ASSISTANT_MODEL_KEY to "gemini-flash-latest", ASSISTANT_STEP_LIMIT_KEY to 40)

            assertTrue(AssistantProviderMigration.shouldMigrate(before))
            val after = AssistantProviderMigration.migrate(before)

            assertNull(after[ASSISTANT_MODEL_KEY])
            assertEquals(40, after[ASSISTANT_STEP_LIMIT_KEY])
        }

    @Test
    fun `nothing to do on a fresh store or with the pinned model`() =
        testScope.runTest {
            assertFalse(AssistantProviderMigration.shouldMigrate(emptyPreferences()))
            val pinned = preferencesOf(ASSISTANT_MODEL_KEY to AssistantConfig.DEFAULT_MODEL)
            assertFalse(AssistantProviderMigration.shouldMigrate(pinned))
        }

    @Test
    fun `a migrated store needs no further migration`() =
        testScope.runTest {
            val before =
                preferencesOf(
                    LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY to "previous-ciphertext",
                    ASSISTANT_MODEL_KEY to "gemini-flash-latest",
                )

            val after = AssistantProviderMigration.migrate(before)

            assertFalse(AssistantProviderMigration.shouldMigrate(after))
            assertEquals(after, AssistantProviderMigration.migrate(after))
        }

    @Test
    fun `a DataStore opened with the migration has no trace of the previous provider`() =
        testScope.runTest {
            val previousInstall =
                seed(
                    preferencesOf(
                        LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY to "previous-ciphertext",
                        ASSISTANT_MODEL_KEY to "gemini-flash-latest",
                        ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY to true,
                    ),
                )
            val dataStore =
                PreferenceDataStoreFactory.create(
                    migrations = listOf(previousInstall, AssistantProviderMigration),
                    scope = testScope.backgroundScope,
                    produceFile = { File(tempDir, "settings.preferences_pb") },
                )

            val prefs = dataStore.data.first()

            assertFalse(LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY in prefs)
            assertNull(prefs[ASSISTANT_MODEL_KEY])
            assertEquals(true, prefs[ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY])
        }

    /** A migration that stands in for the data an install of the previous version left behind. */
    private fun seed(data: Preferences): DataMigration<Preferences> =
        object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData.asMap().isEmpty()

            override suspend fun migrate(currentData: Preferences): Preferences = data

            override suspend fun cleanUp() = Unit
        }
}
