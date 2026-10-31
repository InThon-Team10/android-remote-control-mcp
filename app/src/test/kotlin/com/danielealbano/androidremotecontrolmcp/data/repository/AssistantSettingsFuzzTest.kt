package com.danielealbano.androidremotecontrolmcp.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import com.danielealbano.androidremotecontrolmcp.testutil.RecordingServerLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("AssistantConfig and AssistantSettingsImpl fuzz")
class AssistantSettingsFuzzTest {
    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private val hostile = listOf("\n", "\r", "\t", "\u0000", "\u001b", "\u007f", "\u00e9", "\u200b", "\ud83d\ude00")

    private fun settings(): AssistantSettingsImpl =
        createAssistantSettings(
            MemoryDataStore(),
            SettingsChangeLogger(RecordingServerLogRepository(), Dispatchers.Unconfined, 0L),
        )

    private fun candidateKey(random: Random): String {
        val core =
            buildString {
                repeat(random.nextInt(31)) { append((0x20 + random.nextInt(0x5F)).toChar()) }
                if (random.nextInt(3) == 0) insert(random.nextInt(length + 1), hostile[random.nextInt(hostile.size)])
            }
        return listOf("", " ", "\n", "\t ")[random.nextInt(4)] + core + listOf("", " ", "\r\n")[random.nextInt(3)]
    }

    @Test
    fun `step limit validation accepts exactly 1 to 100`() {
        val edges = listOf(Int.MIN_VALUE, -1, 0, 1, 2, 99, 100, 101, Int.MAX_VALUE)
        val values = edges + List(2000) { Random(it.toLong()).nextInt() }
        for (value in values) {
            assertEquals(value in 1..100, AssistantConfig.validateStepLimit(value).isSuccess, "value=$value")
        }
    }

    @Test
    fun `setStepLimit stores valid values and rejects the rest without changing the stored one`() =
        runTest {
            for (seed in AssistantFuzzGen.SEEDS) {
                val random = Random(seed)
                val settings = settings()
                var expected = AssistantConfig.DEFAULT_STEP_LIMIT
                repeat(60) { index ->
                    val value = if (random.nextBoolean()) random.nextInt(-5, 110) else random.nextInt()
                    val result = runCatching { settings.setStepLimit(value) }
                    if (value in 1..100) {
                        expected = value
                        assertTrue(result.isSuccess, "seed=$seed index=$index value=$value")
                    } else {
                        val error = result.exceptionOrNull()
                        assertTrue(error is IllegalArgumentException, "seed=$seed index=$index v=$value")
                    }
                    assertEquals(expected, settings.getConfig().stepLimit, "seed=$seed index=$index value=$value")
                }
            }
        }

    @Test
    fun `setModel accepts only listed models`() =
        runTest {
            for (seed in AssistantFuzzGen.SEEDS) {
                val random = Random(seed)
                val settings = settings()
                var expected = AssistantConfig.DEFAULT_MODEL
                repeat(40) { index ->
                    val model =
                        when (random.nextInt(3)) {
                            0 -> AssistantConfig.MODELS[random.nextInt(AssistantConfig.MODELS.size)]
                            1 -> AssistantConfig.MODELS[0].uppercase()
                            else -> AssistantConfig.MODELS[0] + AssistantFuzzGen.string(random, 3)
                        }
                    val result = runCatching { settings.setModel(model) }
                    if (model in AssistantConfig.MODELS) expected = model
                    assertEquals(model in AssistantConfig.MODELS, result.isSuccess, "seed=$seed index=$index m=$model")
                    assertEquals(expected, settings.getConfig().model, "seed=$seed index=$index")
                }
            }
        }

    @Test
    fun `setApiKey stores a sendable trimmed key and rejects blank or unsendable ones`() =
        runTest {
            var rejectedUnsendable = 0
            for (seed in AssistantFuzzGen.SEEDS) {
                val random = Random(seed)
                val settings = settings()
                var expected: String? = null
                repeat(60) { index ->
                    val key = candidateKey(random)
                    val trimmed = key.trim()
                    val sendable = trimmed.all { it.code in 0x20..0x7E }
                    val result = runCatching { settings.setApiKey(key) }
                    if (trimmed.isNotEmpty() && sendable) {
                        expected = trimmed
                        assertTrue(result.isSuccess, "seed=$seed index=$index key=${key.map { it.code }}")
                    } else {
                        if (trimmed.isNotEmpty()) rejectedUnsendable++
                        assertTrue(
                            result.exceptionOrNull() is IllegalArgumentException,
                            "seed=$seed index=$index key=${key.map { it.code }} was accepted",
                        )
                    }
                    assertEquals(expected, settings.getApiKey(), "seed=$seed index=$index")
                    assertEquals(expected != null, settings.getConfig().apiKeyConfigured, "seed=$seed index=$index")
                }
                settings.clearApiKey()
                assertNull(settings.getApiKey())
            }
            assertTrue(rejectedUnsendable > 0, "generator produced no unsendable keys")
        }
}
