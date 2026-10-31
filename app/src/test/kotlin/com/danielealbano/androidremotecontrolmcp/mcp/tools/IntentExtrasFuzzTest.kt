package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.services.intents.IntentDispatcher
import com.danielealbano.androidremotecontrolmcp.services.intents.SendIntentRequest
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.random.Random

@DisplayName("send_intent extras conversion property tests")
class IntentExtrasFuzzTest {
    private lateinit var dispatcher: IntentDispatcher
    private lateinit var handler: SendIntentHandler
    private val captured = slot<SendIntentRequest>()

    @BeforeEach
    fun setUp() {
        dispatcher = mockk()
        coEvery { dispatcher.sendIntent(capture(captured)) } returns Result.success(Unit)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        handler = SendIntentHandler(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun digits(
        rnd: Random,
        count: Int,
    ): String = String(CharArray(count) { '0' + rnd.nextInt(10) })

    private fun jsonNumberLiteral(rnd: Random): Pair<String, Boolean> {
        val sign = if (rnd.nextBoolean()) "-" else ""
        val intPart =
            when (rnd.nextInt(4)) {
                0 -> "0"
                1 -> (1 + rnd.nextInt(9)).toString() + digits(rnd, rnd.nextInt(0, 5))
                2 -> (1 + rnd.nextInt(9)).toString() + digits(rnd, rnd.nextInt(17, 21))
                else -> (1 + rnd.nextInt(9)).toString() + digits(rnd, rnd.nextInt(0, 400))
            }
        val frac = if (rnd.nextInt(3) == 0) "." + digits(rnd, rnd.nextInt(1, 25)) else ""
        val exp =
            if (rnd.nextInt(4) == 0) {
                listOf("e", "E")[rnd.nextInt(2)] + listOf("", "+", "-")[rnd.nextInt(3)] + rnd.nextInt(0, 400)
            } else {
                ""
            }
        return "$sign$intPart$frac$exp" to (frac.isEmpty() && exp.isEmpty())
    }

    private suspend fun send(extras: JsonObject): Result<Map<String, Any?>?> {
        val args = JsonObject(mapOf("type" to JsonPrimitive("activity"), "extras" to extras))
        return runCatching {
            handler.execute(args)
            captured.captured.extras
        }
    }

    @Test
    fun `numeric extras are exact longs, finite doubles or InvalidParams and never a raw exception`() =
        runTest {
            val seed = 0x1E7001L
            val rnd = Random(seed)
            var longs = 0
            var doubles = 0
            var rejected = 0
            repeat(ITERATIONS) { i ->
                val (literal, integerLiteral) = jsonNumberLiteral(rnd)
                val element = Json.parseToJsonElement(literal)
                val ctx = "seed=$seed iteration=$i literal=$literal"
                val outcome = send(JsonObject(mapOf("k" to element)))
                val failure = outcome.exceptionOrNull()
                if (failure != null) {
                    assertTrue(failure is McpToolException.InvalidParams, "$ctx raw exception $failure")
                    rejected++
                }
                if (integerLiteral) {
                    val big = BigInteger(literal)
                    if (big.bitLength() <= Long.SIZE_BITS - 1) {
                        assertEquals(mapOf<String, Any?>("k" to big.toLong()), outcome.getOrNull(), ctx)
                        longs++
                    } else {
                        assertTrue(failure is McpToolException.InvalidParams, "$ctx should be rejected")
                    }
                } else {
                    val expected = literal.toDouble()
                    if (expected.isFinite()) {
                        assertEquals(mapOf<String, Any?>("k" to expected), outcome.getOrNull(), ctx)
                        doubles++
                    } else {
                        assertTrue(failure is McpToolException.InvalidParams, "$ctx should be rejected")
                    }
                }
            }
            assertTrue(longs > 0 && doubles > 0 && rejected > 0, "longs=$longs doubles=$doubles rejected=$rejected")
        }

    private fun randomValue(rnd: Random): JsonElement =
        when (rnd.nextInt(8)) {
            0 -> JsonNull
            1 -> JsonPrimitive(rnd.nextBoolean())
            2 -> JsonPrimitive(FuzzStrings.random(rnd, 12))
            3 -> JsonPrimitive(listOf("true", "false", "12", "1.5", "null")[rnd.nextInt(5)])
            4 -> JsonArray(List(rnd.nextInt(0, 4)) { JsonPrimitive(FuzzStrings.random(rnd, 6)) })
            5 -> JsonArray(List(rnd.nextInt(1, 4)) { randomValue(rnd) })
            6 -> JsonObject(mapOf("a" to JsonPrimitive(1)))
            else -> Json.parseToJsonElement(jsonNumberLiteral(rnd).first)
        }

    @Test
    fun `mixed extras only convert strings, booleans, string arrays and numbers, otherwise skip or InvalidParams`() =
        runTest {
            val seed = 0x1E7002L
            val rnd = Random(seed)
            repeat(ITERATIONS) { i ->
                val extras =
                    JsonObject(
                        buildMap {
                            repeat(rnd.nextInt(0, 5)) { put(FuzzStrings.random(rnd, 6), randomValue(rnd)) }
                        },
                    )
                val ctx = "seed=$seed iteration=$i extras=${FuzzStrings.escape(extras.toString())}"
                val outcome = send(extras)
                val failure = outcome.exceptionOrNull()
                if (failure != null) {
                    assertTrue(failure is McpToolException.InvalidParams, "$ctx raw exception $failure")
                    return@repeat
                }
                val result = outcome.getOrNull().orEmpty()
                for ((key, value) in extras) {
                    val converted = result[key]
                    when {
                        value is JsonNull || value is JsonObject -> {
                            assertFalse(result.containsKey(key), "$ctx $key must be skipped")
                        }

                        value is JsonPrimitive && value.isString -> {
                            assertEquals(value.content, converted, "$ctx $key")
                        }

                        value is JsonPrimitive && (value.content == "true" || value.content == "false") -> {
                            assertEquals(value.content == "true", converted, "$ctx $key")
                        }

                        value is JsonArray -> {
                            val strings = value.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                            if (strings.all { it != null }) {
                                assertEquals(strings, converted, "$ctx $key")
                            } else {
                                assertFalse(result.containsKey(key), "$ctx $key must be skipped")
                            }
                        }

                        else -> {
                            assertTrue(converted is Long || converted is Double, "$ctx $key was $converted")
                        }
                    }
                }
            }
        }

    private companion object {
        const val ITERATIONS = 3000
    }
}
