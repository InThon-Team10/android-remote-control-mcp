package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.random.Random

@DisplayName("McpToolUtils property tests")
class McpToolUtilsFuzzTest {
    private val strictNumber = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")
    private val numberParts = Regex("""(-?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?""")

    /** The exact in-range-able integer value of a strict JSON number, or null when it is not an integer or is huge. */
    private fun exactInteger(content: String): BigInteger? {
        val m = numberParts.matchEntire(content) ?: return null
        val frac = m.groupValues[3]
        val exp = BigInteger(m.groupValues[4].ifEmpty { "0" }.removePrefix("+"))
        val mantissa = BigInteger(m.groupValues[2] + frac)
        val e10 = exp.subtract(BigInteger.valueOf(frac.length.toLong()))
        val magnitude = if (mantissa.signum() == 0) BigInteger.ZERO else scaleExactly(mantissa, e10)
        return if (m.groupValues[1] == "-") magnitude?.negate() else magnitude
    }

    private fun scaleExactly(
        mantissa: BigInteger,
        e10: BigInteger,
    ): BigInteger? =
        when {
            e10.signum() >= 0 -> {
                if (e10 > BigInteger.valueOf(HUGE_EXPONENT)) {
                    null
                } else {
                    mantissa.multiply(BigInteger.TEN.pow(e10.toInt()))
                }
            }

            else -> {
                divideExactly(mantissa, e10.negate())
            }
        }

    private fun divideExactly(
        mantissa: BigInteger,
        digitsToDrop: BigInteger,
    ): BigInteger? {
        if (digitsToDrop > BigInteger.valueOf(mantissa.toString().length.toLong())) return null
        val (quotient, remainder) = mantissa.divideAndRemainder(BigInteger.TEN.pow(digitsToDrop.toInt()))
        return if (remainder.signum() == 0) quotient else null
    }

    private fun digits(
        rnd: Random,
        count: Int,
    ): String = String(CharArray(count) { ('0'.code + rnd.nextInt(DECIMAL)).toChar() })

    private fun integerPart(rnd: Random): String =
        when (rnd.nextInt(5)) {
            0 -> {
                "0"
            }

            1 -> {
                listOf(
                    Int.MAX_VALUE.toLong(),
                    Int.MIN_VALUE.toLong(),
                    Long.MAX_VALUE,
                    Long.MIN_VALUE,
                ).random(rnd).toString().removePrefix("-")
            }

            2 -> {
                rnd.nextLong(1, Long.MAX_VALUE).toString()
            }

            3 -> {
                digits(rnd, rnd.nextInt(1, 26)).trimStart('0').ifEmpty { "7" }
            }

            else -> {
                rnd.nextInt(1, 100).toString()
            }
        }

    private fun fractionPart(rnd: Random): String {
        val allZeros = rnd.nextInt(3) == 0
        val count = rnd.nextInt(1, 6)
        return "." + if (allZeros) "0".repeat(count) else digits(rnd, count)
    }

    private fun exponentPart(rnd: Random): String {
        val marker = if (rnd.nextBoolean()) "e" else "E"
        val sign = listOf("-", "+", "")[rnd.nextInt(3)]
        val magnitude =
            when (rnd.nextInt(4)) {
                0 -> rnd.nextInt(0, 5).toString()
                1 -> rnd.nextInt(0, 400).toString()
                2 -> rnd.nextInt(Int.MAX_VALUE - 5, Int.MAX_VALUE).toString()
                else -> "9".repeat(rnd.nextInt(10, 14))
            }
        return marker + sign + magnitude
    }

    private fun literal(rnd: Random): String {
        val sb = StringBuilder()
        if (rnd.nextInt(3) == 0) sb.append('-')
        sb.append(integerPart(rnd))
        if (rnd.nextInt(3) == 0) sb.append(fractionPart(rnd))
        if (rnd.nextInt(3) == 0) sb.append(exponentPart(rnd))
        if (rnd.nextInt(5) == 0) mutate(rnd, sb)
        return sb.toString()
    }

    private fun mutate(
        rnd: Random,
        sb: StringBuilder,
    ) {
        val piece = FRAGMENTS[rnd.nextInt(FRAGMENTS.size)]
        when (rnd.nextInt(3)) {
            0 -> {
                sb.insert(rnd.nextInt(sb.length + 1), piece)
            }

            1 -> {
                sb.append(piece)
            }

            else -> {
                sb.setLength(0)
                sb.append(piece)
            }
        }
    }

    private class Outcome(
        val value: Any?,
        val error: Throwable?,
    )

    private fun run(block: () -> Any?): Outcome {
        val start = System.nanoTime()
        val outcome =
            try {
                Outcome(block(), null)
            } catch (e: Throwable) {
                Outcome(null, e)
            }
        val elapsedMs = (System.nanoTime() - start) / NANOS_PER_MS
        assertTrue(elapsedMs < MAX_CALL_MS, "call took $elapsedMs ms")
        return outcome
    }

    /** An exponent beyond Int range is not representable by BigDecimal, so a safe rejection is acceptable there. */
    private fun hasUnrepresentableExponent(content: String): Boolean =
        content
            .substringAfter('e', content.substringAfter('E', ""))
            .trimStart('+', '-')
            .trimStart('0')
            .length > MAX_EXPONENT_DIGITS

    private fun assertSafeFailure(
        ctx: String,
        error: Throwable?,
    ) {
        if (error != null) {
            val type = error::class.simpleName
            assertTrue(error is McpToolException.InvalidParams, "$ctx threw $type: ${error.message}")
        }
    }

    private fun assertNumber(
        ctx: String,
        content: String,
        outcome: Outcome,
        expected: BigInteger?,
        range: ClosedRange<BigInteger>,
    ) {
        assertSafeFailure(ctx, outcome.error)
        // kotlinx.serialization lets lenient numeric literals such as "32." through; only the strict JSON grammar has a
        // single exact value to compare against, anything else just has to fail safely.
        if (!strictNumber.matches(content)) return
        val expectedAccepted = expected != null && expected in range
        val value = outcome.value
        if (value != null) {
            assertTrue(expectedAccepted, "$ctx accepted $value but the exact value is no in-range integer ($expected)")
            assertEquals(expected, BigInteger.valueOf((value as Number).toLong()), ctx)
        } else if (expectedAccepted && !hasUnrepresentableExponent(content)) {
            throw AssertionError("$ctx rejected an exact in-range integer $expected")
        }
    }

    private fun parseLiteral(content: String): JsonElement? =
        runCatching { (Json.parseToJsonElement("""{"v":$content}""") as JsonObject)["v"] }.getOrNull()

    @Test
    fun `integer extractors return the exact integer or reject with InvalidParams`() {
        val seed = 0x7001L
        val rnd = Random(seed)
        var parsed = 0
        val longRange = BigInteger.valueOf(Long.MIN_VALUE)..BigInteger.valueOf(Long.MAX_VALUE)
        val intRange = BigInteger.valueOf(Int.MIN_VALUE.toLong())..BigInteger.valueOf(Int.MAX_VALUE.toLong())
        repeat(ITERATIONS) { i ->
            val content = literal(rnd)
            val element = parseLiteral(content) ?: return@repeat
            parsed++
            val params = buildJsonObject { put("v", element) }
            val ctx = "seed=$seed iteration=$i content='${FuzzStrings.escape(content)}'"
            val expected = exactInteger(content)
            val longs = listOf("requireLong", "optionalLong")
            val ints = listOf("requireInt", "optionalInt")
            val outcomes =
                mapOf(
                    longs[0] to run { McpToolUtils.requireLong(params, "v") },
                    longs[1] to run { McpToolUtils.optionalLong(params, "v", 0L) },
                    ints[0] to run { McpToolUtils.requireInt(params, "v") },
                    ints[1] to run { McpToolUtils.optionalInt(params, "v", 0) },
                )
            outcomes.forEach { (name, outcome) ->
                val range = if (name in longs) longRange else intRange
                assertNumber("$name $ctx", content, outcome, expected, range)
            }
        }
        assertTrue(parsed > ITERATIONS / 2, "only $parsed literals survived JSON parsing")
    }

    @Test
    fun `float extractors return the correctly rounded finite value or reject with InvalidParams`() {
        val seed = 0x7002L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val content = literal(rnd)
            val element = parseLiteral(content) ?: return@repeat
            val params = buildJsonObject { put("v", element) }
            val ctx = "seed=$seed iteration=$i content='${FuzzStrings.escape(content)}'"
            val expected = content.toFloatOrNull()?.takeIf { it.isFinite() }
            val outcomes =
                mapOf(
                    "requireFloat" to run { McpToolUtils.requireFloat(params, "v") },
                    "optionalFloat" to run { McpToolUtils.optionalFloat(params, "v", 0f) },
                )
            outcomes.forEach { (name, outcome) ->
                assertSafeFailure("$name $ctx", outcome.error)
                if (!strictNumber.matches(content)) return@forEach
                if (outcome.value != null) {
                    assertEquals(expected, outcome.value, "$name $ctx")
                } else {
                    assertNull(expected, "$name $ctx rejected a finite float")
                }
            }
        }
    }

    private fun randomElement(rnd: Random): JsonElement =
        when (rnd.nextInt(7)) {
            0 -> JsonPrimitive(FuzzStrings.random(rnd, 30))
            1 -> JsonPrimitive(rnd.nextLong())
            2 -> JsonPrimitive(rnd.nextBoolean())
            3 -> JsonNull
            4 -> JsonObject(mapOf("k" to JsonPrimitive(1)))
            5 -> JsonArray(listOf(JsonPrimitive(1)))
            else -> JsonPrimitive(rnd.nextDouble())
        }

    private fun isJsonBoolean(element: JsonElement): Boolean =
        element is JsonPrimitive && !element.isString && element.content in listOf("true", "false")

    @Test
    fun `string and boolean extractors accept only their own JSON type and never throw anything else`() {
        val seed = 0x7003L
        val rnd = Random(seed)
        repeat(ITERATIONS) { i ->
            val element = randomElement(rnd)
            val params = buildJsonObject { put("v", element) }
            val ctx = "seed=$seed iteration=$i element=${FuzzStrings.escape(element.toString())}"
            val string = run { McpToolUtils.requireString(params, "v") }
            val optionalString = run { McpToolUtils.optionalString(params, "v", "d") }
            val boolean = run { McpToolUtils.optionalBoolean(params, "v", false) }
            listOf(string, optionalString, boolean).forEach { assertSafeFailure(ctx, it.error) }
            if (element is JsonPrimitive && element.isString) {
                assertEquals(element.content, string.value, ctx)
                assertEquals(element.content, optionalString.value, ctx)
            } else {
                assertTrue(string.error != null, "$ctx requireString accepted a non-string")
                assertTrue(optionalString.error != null, "$ctx optionalString accepted a non-string")
            }
            assertEquals(isJsonBoolean(element), boolean.error == null, ctx)
        }
    }

    private companion object {
        const val ITERATIONS = 6000
        const val HUGE_EXPONENT = 60L
        const val DECIMAL = 10
        val FRAGMENTS =
            listOf(
                "NaN",
                "Infinity",
                "-Infinity",
                "0x10",
                "d",
                "f",
                "L",
                "_",
                "+",
                ".",
                "e",
                "--",
                "0x1p3",
                "00",
                "..",
                "e+",
            )
        const val MAX_EXPONENT_DIGITS = 9
        const val MAX_CALL_MS = 1000L
        const val NANOS_PER_MS = 1_000_000L
    }
}
