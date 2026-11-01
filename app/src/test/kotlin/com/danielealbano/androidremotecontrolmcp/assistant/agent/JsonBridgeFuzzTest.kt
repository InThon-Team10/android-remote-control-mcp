package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.random.Random

@DisplayName("JsonBridge fuzz")
class JsonBridgeFuzzTest {
    /** Structural equality where numbers compare by value (a double and its exact text are the same number). */
    private fun same(
        a: JsonElement,
        b: JsonElement,
    ): Boolean =
        when {
            a is JsonObject && b is JsonObject -> sameObject(a, b)
            a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
            a is JsonPrimitive && b is JsonPrimitive -> samePrimitive(a, b)
            else -> false
        }

    private fun sameObject(
        a: JsonObject,
        b: JsonObject,
    ): Boolean = a.keys == b.keys && a.all { (key, value) -> same(value, b.getValue(key)) }

    private fun samePrimitive(
        a: JsonPrimitive,
        b: JsonPrimitive,
    ): Boolean =
        when {
            a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
            a.isString || b.isString -> a.isString == b.isString && a.contentOrNull == b.contentOrNull
            else -> a.content == b.content || sameNumber(a.content, b.content)
        }

    private fun sameNumber(
        a: String,
        b: String,
    ): Boolean = runCatching { BigDecimal(a).compareTo(BigDecimal(b)) == 0 }.getOrDefault(false)

    @Test
    fun `random JSON objects survive the Map round trip`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(250) { index ->
                val original = AssistantFuzzGen.jsonObject(random, 4)
                val back = JsonBridge.toJsonObject(JsonBridge.toMap(original))
                check(same(original, back)) { "seed=$seed index=$index\n$original\n$back" }
            }
        }
    }

    @Test
    fun `nesting up to 400 levels round trips in both shapes`() {
        for (depth in listOf(1, 50, 200, 400)) {
            for (asArray in listOf(false, true)) {
                val original = JsonObject(mapOf("root" to AssistantFuzzGen.nested(depth, asArray)))
                val back = JsonBridge.toJsonObject(JsonBridge.toMap(original))
                assertEquals(original, back, "depth=$depth array=$asArray")
            }
        }
    }

    @Test
    fun `strings keep every unicode and control character`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(100) { index ->
                val key = AssistantFuzzGen.string(random, 12)
                val value = AssistantFuzzGen.string(random, 40)
                val original = JsonObject(mapOf(key to JsonPrimitive(value)))
                assertEquals(original, JsonBridge.toJsonObject(JsonBridge.toMap(original)), "seed=$seed index=$index")
            }
        }
    }
}
