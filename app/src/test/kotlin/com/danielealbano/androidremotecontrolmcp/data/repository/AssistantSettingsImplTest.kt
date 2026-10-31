package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.cash.turbine.test
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.model.HelperFlag
import com.danielealbano.androidremotecontrolmcp.data.model.ServerLogEntry
import com.danielealbano.androidremotecontrolmcp.data.security.ApiKeyCipher
import com.danielealbano.androidremotecontrolmcp.data.security.BuiltInApiKeyProvider
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.GeneralSecurityException

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("AssistantSettingsImpl")
class AssistantSettingsImplTest {
    @TempDir
    lateinit var tempDir: File

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val serverLog = RecordingServerLogRepository()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var changeLogger: SettingsChangeLogger
    private lateinit var settings: AssistantSettingsImpl

    @BeforeEach
    fun setUp() {
        dataStore =
            PreferenceDataStoreFactory.create(
                scope = testScope.backgroundScope,
                produceFile = { File(tempDir, "assistant_settings.preferences_pb") },
            )
        changeLogger = SettingsChangeLogger(serverLog, Dispatchers.Unconfined, 0L)
        settings = createAssistantSettings(dataStore, changeLogger)
    }

    private fun settingsMessages() = serverLog.ofType(ServerLogEntry.Type.SETTINGS).map { it.message }

    @Test
    fun `keeps the DataStore key names`() {
        assertEquals("assistant_enabled", ASSISTANT_ENABLED_KEY.name)
        assertEquals("assistant_voice_enabled", ASSISTANT_VOICE_ENABLED_KEY.name)
        assertEquals("assistant_model", ASSISTANT_MODEL_KEY.name)
        assertEquals("assistant_step_limit", ASSISTANT_STEP_LIMIT_KEY.name)
        assertEquals("assistant_privacy_notice_accepted", ASSISTANT_PRIVACY_NOTICE_ACCEPTED_KEY.name)
        assertEquals("assistant_openai_api_key_ciphertext", ASSISTANT_API_KEY_CIPHERTEXT_KEY.name)
        assertEquals("assistant_api_key_ciphertext", LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY.name)
    }

    @Test
    fun `defaults on a fresh store`() =
        testScope.runTest {
            assertEquals(AssistantConfig(), settings.getConfig())
            assertNull(settings.getApiKey())
        }

    @Test
    fun `setEnabled persists and logs`() =
        testScope.runTest {
            settings.setEnabled(true)
            assertTrue(settings.config.first().enabled)
            settings.setEnabled(false)
            assertFalse(settings.config.first().enabled)

            assertEquals(listOf("Assistant enabled", "Assistant disabled"), settingsMessages())
        }

    @Test
    fun `setVoiceEnabled persists and logs`() =
        testScope.runTest {
            settings.setVoiceEnabled(true)
            assertTrue(settings.config.first().voiceEnabled)
            settings.setVoiceEnabled(false)
            assertFalse(settings.config.first().voiceEnabled)

            assertEquals(
                listOf("Assistant voice input enabled", "Assistant voice input disabled"),
                settingsMessages(),
            )
        }

    @Test
    fun `acceptPrivacyNotice persists and logs`() =
        testScope.runTest {
            settings.acceptPrivacyNotice()

            assertTrue(settings.config.first().privacyNoticeAccepted)
            assertEquals(listOf("Assistant privacy notice accepted"), settingsMessages())
        }

    @Test
    fun `a model saved for the previous provider reads as the pinned model and cannot be set again`() =
        testScope.runTest {
            dataStore.edit { it[ASSISTANT_MODEL_KEY] = "gemini-flash-latest" }

            assertEquals("gpt-6.1-terra", settings.config.first().model)
            assertFailsWith<IllegalArgumentException> { settings.setModel("gemini-flash-latest") }
        }

    @Test
    fun `setModel rejects an unknown model`() =
        testScope.runTest {
            assertFailsWith<IllegalArgumentException> { settings.setModel("foo") }

            assertNull(dataStore.data.first()[ASSISTANT_MODEL_KEY])
            assertEquals(AssistantConfig.DEFAULT_MODEL, settings.config.first().model)
        }

    @Test
    fun `unknown stored model falls back to default`() =
        testScope.runTest {
            dataStore.edit { it[ASSISTANT_MODEL_KEY] = "foo" }

            assertEquals(AssistantConfig.DEFAULT_MODEL, settings.config.first().model)
        }

    @Test
    fun `setStepLimit persists 1 and 100`() =
        testScope.runTest {
            settings.setStepLimit(1)
            assertEquals(1, dataStore.data.first()[ASSISTANT_STEP_LIMIT_KEY])
            settings.setStepLimit(100)
            assertEquals(100, dataStore.data.first()[ASSISTANT_STEP_LIMIT_KEY])
            assertEquals(100, settings.config.first().stepLimit)
        }

    @Test
    fun `setStepLimit logs the change`() =
        testScope.runTest {
            settings.setStepLimit(50)

            assertEquals(listOf("Assistant step limit changed 30 → 50"), settingsMessages())
        }

    @Test
    fun `setStepLimit rejects 0 and 101`() =
        testScope.runTest {
            listOf(0, 101).forEach { value ->
                assertFailsWith<IllegalArgumentException> { settings.setStepLimit(value) }
            }

            assertNull(dataStore.data.first()[ASSISTANT_STEP_LIMIT_KEY])
            assertEquals(30, settings.config.first().stepLimit)
        }

    @Test
    fun `out-of-range stored step limit falls back to default`() =
        testScope.runTest {
            dataStore.edit { it[ASSISTANT_STEP_LIMIT_KEY] = 500 }

            assertEquals(30, settings.config.first().stepLimit)
        }

    @Test
    fun `unchanged values are not logged`() =
        testScope.runTest {
            settings.setEnabled(false)
            settings.setVoiceEnabled(false)
            settings.setModel(AssistantConfig.DEFAULT_MODEL)
            settings.setStepLimit(AssistantConfig.DEFAULT_STEP_LIMIT)
            settings.clearApiKey()

            assertTrue(settingsMessages().isEmpty())
        }

    @Test
    fun `setApiKey stores trimmed key and getApiKey returns it`() =
        testScope.runTest {
            settings.setApiKey("  key  ")

            assertEquals("key", settings.getApiKey())
            assertTrue(settings.config.first().apiKeyConfigured)
        }

    @Test
    fun `setApiKey rejects blank`() =
        testScope.runTest {
            listOf("", "   ").forEach { value ->
                assertFailsWith<IllegalArgumentException> { settings.setApiKey(value) }
            }

            assertNull(dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY])
        }

    @Test
    fun `setApiKey propagates cipher failure and stores nothing`() =
        testScope.runTest {
            val failing = mockk<ApiKeyCipher>()
            every { failing.encrypt(any()) } throws GeneralSecurityException("no key")
            val failingSettings =
                AssistantSettingsImpl(
                    dataStore,
                    failing,
                    BuiltInApiKeyProvider { null },
                    changeLogger,
                    Dispatchers.Unconfined,
                )

            assertFailsWith<GeneralSecurityException> { failingSettings.setApiKey("key") }

            assertNull(dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY])
            assertTrue(settingsMessages().isEmpty())
        }

    @Test
    fun `stored value is ciphertext never equal to the plaintext`() =
        testScope.runTest {
            settings.setApiKey("example-user-key-123")

            val raw = dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY]!!
            assertNotEquals("example-user-key-123", raw)
            assertFalse(raw.contains("example-user-key-123"))
        }

    @Test
    fun `clearApiKey removes the key`() =
        testScope.runTest {
            settings.setApiKey("key")
            settings.clearApiKey()

            assertNull(settings.getApiKey())
            assertFalse(settings.config.first().apiKeyConfigured)
            assertNull(dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY])
        }

    @Test
    fun `config re-emits apiKeyConfigured after set and clear`() =
        testScope.runTest {
            settings.config.test {
                assertFalse(awaitItem().apiKeyConfigured)
                settings.setApiKey("key")
                assertTrue(awaitItem().apiKeyConfigured)
                settings.clearApiKey()
                assertFalse(awaitItem().apiKeyConfigured)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `undecryptable stored key reports not configured`() =
        testScope.runTest {
            dataStore.edit { it[ASSISTANT_API_KEY_CIPHERTEXT_KEY] = "garbage-not-a-ciphertext" }

            assertNull(settings.getApiKey())
            assertFalse(settings.config.first().apiKeyConfigured)
        }

    @Test
    fun `server log never contains the key or its ciphertext`() =
        testScope.runTest {
            settings.setApiKey("first-secret-key")
            val firstCiphertext = dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY]!!
            settings.setApiKey("second-secret-key")
            val secondCiphertext = dataStore.data.first()[ASSISTANT_API_KEY_CIPHERTEXT_KEY]!!
            settings.clearApiKey()

            val messages = settingsMessages()
            assertEquals(
                listOf("Assistant API key changed", "Assistant API key changed", "Assistant API key cleared"),
                messages,
            )
            messages.forEach { message ->
                listOf("first-secret-key", "second-secret-key", firstCiphertext, secondCiphertext).forEach {
                    assertFalse(message.contains(it))
                }
            }
        }

    @Test
    fun `replacing the key with the same plaintext still logs a change`() =
        testScope.runTest {
            settings.setApiKey("k")
            settings.setApiKey("k")

            assertEquals(listOf("Assistant API key changed", "Assistant API key changed"), settingsMessages())
        }

    private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue(failure is T, "expected ${T::class.simpleName} but was $failure")
    }

    @Test
    fun `built-in key is the fallback and flags the config`() =
        testScope.runTest {
            val withBuiltIn = createAssistantSettings(dataStore, changeLogger, builtInKey = "built-in-key")

            assertEquals("built-in-key", withBuiltIn.getApiKey())
            val config = withBuiltIn.getConfig()
            assertTrue(config.apiKeyConfigured)
            assertTrue(config.builtInApiKeyInUse)
        }

    @Test
    fun `saved key has priority over the built-in key and clearing it restores the fallback`() =
        testScope.runTest {
            val withBuiltIn = createAssistantSettings(dataStore, changeLogger, builtInKey = "built-in-key")

            withBuiltIn.setApiKey("user-key")
            assertEquals("user-key", withBuiltIn.getApiKey())
            assertTrue(withBuiltIn.getConfig().apiKeyConfigured)
            assertFalse(withBuiltIn.getConfig().builtInApiKeyInUse)

            withBuiltIn.clearApiKey()
            assertEquals("built-in-key", withBuiltIn.getApiKey())
            assertTrue(withBuiltIn.getConfig().builtInApiKeyInUse)
        }

    @Test
    fun `a key saved for the previous provider is never used and the built-in key stays in use`() =
        testScope.runTest {
            val withBuiltIn = createAssistantSettings(dataStore, changeLogger, builtInKey = "built-in-key")
            withBuiltIn.storeUnderLegacyKey("previous-provider-key")

            assertEquals("built-in-key", withBuiltIn.getApiKey())
            val config = withBuiltIn.getConfig()
            assertTrue(config.apiKeyConfigured)
            assertTrue(config.builtInApiKeyInUse)
        }

    @Test
    fun `a key saved for the previous provider does not count as configured without a built-in key`() =
        testScope.runTest {
            settings.storeUnderLegacyKey("previous-provider-key")

            assertNull(settings.getApiKey())
            assertFalse(settings.getConfig().apiKeyConfigured)
            assertFalse(settings.getConfig().builtInApiKeyInUse)
        }

    /** Encrypts [key] with this instance's cipher (so it could be decrypted) and moves it to the legacy preference. */
    private suspend fun AssistantSettingsImpl.storeUnderLegacyKey(key: String) {
        setApiKey(key)
        dataStore.edit { prefs ->
            val ciphertext = checkNotNull(prefs[ASSISTANT_API_KEY_CIPHERTEXT_KEY])
            prefs.remove(ASSISTANT_API_KEY_CIPHERTEXT_KEY)
            prefs[LEGACY_ASSISTANT_API_KEY_CIPHERTEXT_KEY] = ciphertext
        }
    }

    @Test
    fun `without saved or built-in key nothing is configured`() =
        testScope.runTest {
            assertNull(settings.getApiKey())
            assertFalse(settings.getConfig().apiKeyConfigured)
            assertFalse(settings.getConfig().builtInApiKeyInUse)
        }

    @Test
    fun `helper flags default and persist`() =
        testScope.runTest {
            assertFalse(settings.getConfig().helperIntroSeen)
            assertTrue(settings.getConfig().helperSpeechEnabled)

            settings.setHelperFlag(HelperFlag.INTRO_SEEN, true)
            settings.setHelperFlag(HelperFlag.SPEECH_ENABLED, false)

            assertTrue(settings.getConfig().helperIntroSeen)
            assertFalse(settings.getConfig().helperSpeechEnabled)
        }
}
