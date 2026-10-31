package com.danielealbano.androidremotecontrolmcp.testutil

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.random.Random

/** Seeded generators shared by the assistant fuzz suites. Every failure message must carry the seed. */
object AssistantFuzzGen {
    val SEEDS: List<Long> = (1L..40L).toList()

    private val ALPHABET: List<String> =
        listOf(
            "a",
            "Z",
            "0",
            " ",
            "_",
            "\"",
            "\\",
            "\n",
            "\t",
            "\u0000",
            "\u0007",
            "\u007f",
            "\u00e9",
            "\u0131",
            "\u0430",
            "\u200b",
            "\u202e",
            "\ufeff",
            "\u2028",
            "\ud83d\ude00",
            "\ud83d",
            "\ude00",
            "\uff43",
            "\u212a",
        )

    fun string(
        random: Random,
        maxLength: Int = 24,
    ): String =
        buildString {
            repeat(random.nextInt(maxLength + 1)) { append(ALPHABET[random.nextInt(ALPHABET.size)]) }
        }

    fun identifier(
        random: Random,
        maxLength: Int = 12,
    ): String = buildString { repeat(1 + random.nextInt(maxLength)) { append("abcxyz_019"[random.nextInt(10)]) } }

    fun primitive(random: Random): JsonElement =
        when (random.nextInt(9)) {
            0 -> JsonNull
            1 -> JsonPrimitive(random.nextBoolean())
            2 -> JsonPrimitive(random.nextInt())
            3 -> JsonPrimitive(random.nextLong())
            4 -> JsonPrimitive(random.nextDouble(-1e12, 1e12))
            5 -> JsonUnquotedLiteral("1" + "0".repeat(1 + random.nextInt(60)))
            6 -> JsonPrimitive(listOf("NaN", "Infinity", "-Infinity", "1e999", "")[random.nextInt(5)])
            7 -> JsonPrimitive(random.nextInt(3) - 1)
            else -> JsonPrimitive(string(random))
        }

    /** Random JSON; [depth] bounds nesting, keys may contain any [ALPHABET] character. */
    fun json(
        random: Random,
        depth: Int,
    ): JsonElement =
        if (depth <= 0 || random.nextInt(3) == 0) {
            primitive(random)
        } else if (random.nextBoolean()) {
            JsonArray(List(random.nextInt(4)) { json(random, depth - 1) })
        } else {
            jsonObject(random, depth)
        }

    fun jsonObject(
        random: Random,
        depth: Int,
    ): JsonObject {
        val entries = LinkedHashMap<String, JsonElement>()
        repeat(random.nextInt(5)) { entries[string(random, 8)] = json(random, depth - 1) }
        return JsonObject(entries)
    }

    fun nested(
        depth: Int,
        asArray: Boolean,
    ): JsonElement {
        var current: JsonElement = JsonPrimitive(1)
        repeat(depth) { current = if (asArray) JsonArray(listOf(current)) else JsonObject(mapOf("k" to current)) }
        return current
    }

    fun secret(random: Random): String =
        "AIzaSy" + buildString { repeat(33) { append("abcdefghijklmnopqrstuvwxyz0123456789"[random.nextInt(36)]) } }
}
