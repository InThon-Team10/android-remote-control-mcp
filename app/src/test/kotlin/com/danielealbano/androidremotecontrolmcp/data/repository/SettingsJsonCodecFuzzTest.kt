package com.danielealbano.androidremotecontrolmcp.data.repository

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.data.model.BuiltinPermissions
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("Settings JSON codec property tests")
class SettingsJsonCodecFuzzTest {
    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun randomLocation(rnd: Random) =
        SettingsRepository.StoredLocation(
            id = FuzzStrings.random(rnd, 20),
            name = FuzzStrings.random(rnd, 20),
            path = FuzzStrings.random(rnd, 30),
            description = FuzzStrings.random(rnd, 40),
            treeUri = FuzzStrings.random(rnd, 40),
            allowWrite = rnd.nextBoolean(),
            allowDelete = rnd.nextBoolean(),
        )

    @Test
    fun `stored locations and builtin permissions round-trip exactly for arbitrary text`() {
        val seed = 0xC0DEC1L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val locations = List(rnd.nextInt(0, 5)) { randomLocation(rnd) }
            val ctx = "seed=$seed iteration=$i"
            assertEquals(locations, parseStoredLocationsJson(serializeStoredLocationsJson(locations)), ctx)
            val permissions =
                buildMap {
                    repeat(rnd.nextInt(0, 5)) {
                        put(FuzzStrings.random(rnd, 12), BuiltinPermissions(rnd.nextBoolean(), rnd.nextBoolean()))
                    }
                }
            assertEquals(permissions, parseBuiltinPermissionsJson(serializeBuiltinPermissions(permissions)), ctx)
        }
    }

    @Test
    fun `parsing arbitrary or corrupted JSON never throws and yields empty results for non-JSON`() {
        val seed = 0xC0DEC2L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val valid = serializeStoredLocationsJson(List(rnd.nextInt(0, 3)) { randomLocation(rnd) })
            val corrupted =
                when (rnd.nextInt(4)) {
                    0 -> {
                        FuzzStrings.random(rnd, 60)
                    }

                    1 -> {
                        valid.substring(0, rnd.nextInt(valid.length + 1))
                    }

                    2 -> {
                        val from = rnd.nextInt(valid.length)
                        valid.removeRange(from, rnd.nextInt(from, valid.length + 1)) + FuzzStrings.random(rnd, 3)
                    }

                    else -> {
                        "{" + FuzzStrings.random(rnd, 40) + "}"
                    }
                }
            val ctx = "seed=$seed iteration=$i json='${FuzzStrings.escape(corrupted)}'"
            assertNull(runCatching { parseStoredLocationsJson(corrupted) }.exceptionOrNull(), ctx)
            assertNull(runCatching { parseBuiltinPermissionsJson(corrupted) }.exceptionOrNull(), ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 4000
    }
}
