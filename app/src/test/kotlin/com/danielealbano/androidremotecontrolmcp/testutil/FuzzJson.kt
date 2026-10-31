package com.danielealbano.androidremotecontrolmcp.testutil

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/**
 * Seeded generators of arbitrary JSON values shared by the MCP fuzz tests. Everything is derived from the
 * caller's [Random] so a failing case is reproducible from its seed and iteration number.
 */
internal object FuzzJson {
    private val NUMBERS: List<Number> =
        listOf(
            0,
            1,
            -1,
            2,
            255,
            -255,
            Int.MAX_VALUE,
            Int.MIN_VALUE,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            0.5,
            -0.0,
            1e308,
            -1e308,
            4.9e-324,
            1e-9,
            3.14159,
            2147483648L,
            4294967296L,
            9007199254740993L,
        )

    private val NAN_LIKE =
        listOf("NaN", "Infinity", "-Infinity", "nan", "inf", "1e999", "-", "", " ", "0x10", "1_000", "--1", "1,5")

    /** A random primitive, mixing booleans, nulls, odd numbers, NaN-like strings and fuzz strings. */
    fun primitive(rnd: Random): JsonElement =
        when (rnd.nextInt(PRIMITIVE_KINDS)) {
            0 -> JsonNull
            1 -> JsonPrimitive(rnd.nextBoolean())
            2 -> JsonPrimitive(NUMBERS[rnd.nextInt(NUMBERS.size)])
            3 -> JsonPrimitive(rnd.nextInt(-BIG_INT, BIG_INT))
            4 -> JsonPrimitive(rnd.nextDouble(-BIG_DOUBLE, BIG_DOUBLE))
            5 -> JsonPrimitive(NAN_LIKE[rnd.nextInt(NAN_LIKE.size)])
            else -> JsonPrimitive(FuzzStrings.random(rnd, SHORT_STRING))
        }

    /** A random JSON value nested at most [maxDepth] levels deep. */
    fun value(
        rnd: Random,
        maxDepth: Int,
    ): JsonElement {
        if (maxDepth <= 0 || rnd.nextInt(CONTAINER_ODDS) != 0) return primitive(rnd)
        return if (rnd.nextBoolean()) {
            JsonArray(List(rnd.nextInt(MAX_CHILDREN)) { value(rnd, maxDepth - 1) })
        } else {
            JsonObject(
                buildMap {
                    repeat(rnd.nextInt(MAX_CHILDREN)) {
                        put(FuzzStrings.random(rnd, KEY_LENGTH), value(rnd, maxDepth - 1))
                    }
                },
            )
        }
    }

    /** `[[[...]]]` or `{"a":{"a":...}}` text of exactly [depth] levels. */
    fun nestedText(
        depth: Int,
        useObjects: Boolean,
    ): String =
        if (useObjects) {
            "{\"a\":".repeat(depth) + "1" + "}".repeat(depth)
        } else {
            "[".repeat(depth) + "1" + "]".repeat(depth)
        }

    /** A JSON array of [length] fuzz primitives, or a very long string. */
    fun huge(
        rnd: Random,
        length: Int,
    ): JsonElement =
        if (rnd.nextBoolean()) {
            JsonArray(List(length) { primitive(rnd) })
        } else {
            JsonPrimitive("h".repeat(length))
        }

    private const val PRIMITIVE_KINDS = 7
    private const val BIG_INT = 1_000_000
    private const val BIG_DOUBLE = 1e7
    private const val SHORT_STRING = 24
    private const val KEY_LENGTH = 8
    private const val MAX_CHILDREN = 5
    private const val CONTAINER_ODDS = 3
}
