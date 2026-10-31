package com.danielealbano.androidremotecontrolmcp.testutil

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** A tool as advertised by `tools/list`: its name and the parts of its input schema the fuzzer reads. */
internal class FuzzTool(
    val name: String,
    val properties: Map<String, JsonObject>,
    val required: Set<String>,
) {
    companion object {
        fun parse(tool: JsonObject): FuzzTool {
            val schema = tool.getValue("inputSchema").jsonObject
            val properties =
                (schema["properties"] as? JsonObject)?.mapValues { it.value.jsonObject }.orEmpty()
            val required = (schema["required"] as? JsonArray)?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
            return FuzzTool(tool.getValue("name").jsonPrimitive.content, properties, required)
        }
    }
}

/** A numeric or length bound for one tool parameter, read from the schema or from the documented description. */
internal class Bound(
    val min: Double?,
    val max: Double?,
    val maxLength: Int? = null,
)

/**
 * Seeded generator of `arguments` objects from a tool's input schema, plus mutation helpers. The schemas of this
 * server carry few `minimum`/`maximum` keywords, so [DOCUMENTED_BOUNDS] adds the ranges the parameter descriptions
 * state ("1-30000", "max 200", ...); both are treated as the contract.
 */
internal class FuzzToolArguments(
    private val rnd: Random,
) {
    /** Bound of [tool].[param]: schema keywords first, then the documented table. */
    fun boundOf(
        tool: FuzzTool,
        param: String,
    ): Bound? {
        val schema = tool.properties[param] ?: return null
        val schemaMin = schema["minimum"]?.jsonPrimitive?.doubleOrNull
        val schemaMax = schema["maximum"]?.jsonPrimitive?.doubleOrNull
        val documented = DOCUMENTED_BOUNDS[tool.name + "." + param]
        return if (schemaMin != null || schemaMax != null) {
            Bound(schemaMin ?: documented?.min, schemaMax ?: documented?.max, documented?.maxLength)
        } else {
            documented
        }
    }

    /** Schema-conforming arguments: every required property plus a random subset of the optional ones. */
    fun conforming(tool: FuzzTool): JsonObject {
        if (tool.name == "android_custom_gesture") return gesturePaths()
        val values =
            buildMap {
                for ((name, schema) in tool.properties) {
                    if (name in tool.required || rnd.nextInt(OPTIONAL_ODDS) != 0) {
                        put(name, conformingValue(tool, name, schema))
                    }
                }
            }.toMutableMap()
        // Node-targeting tools take either a node_id or a by/value selector, never both.
        if ("node_id" in tool.properties && "by" in tool.properties && "node_id" !in tool.required) {
            val selector = listOf("by", "value", "exact_match")
            if (rnd.nextBoolean()) values.remove("node_id") else selector.forEach { values.remove(it) }
            if ("node_id" !in values) {
                values["by"] = values["by"] ?: conformingValue(tool, "by", tool.properties.getValue("by"))
                values["value"] = values["value"] ?: conformingValue(tool, "value", tool.properties.getValue("value"))
            }
        }
        return JsonObject(values)
    }

    private fun conformingValue(
        tool: FuzzTool,
        name: String,
        schema: JsonObject,
    ): JsonElement {
        val enum = schema["enum"] as? JsonArray
        if (enum != null) return enum[rnd.nextInt(enum.size)]
        val bound = boundOf(tool, name)
        return when (schema["type"]?.jsonPrimitive?.contentOrNull) {
            "boolean" -> JsonPrimitive(rnd.nextBoolean())
            "integer" -> JsonPrimitive(inRange(bound, name).toLong())
            "number" -> JsonPrimitive(conformingNumber(bound, name))
            "array" -> conformingArray(tool, name, schema)
            "object" -> conformingObject(schema)
            else -> JsonPrimitive(conformingString(name, bound))
        }
    }

    /** custom_gesture needs strictly increasing times, which the schema cannot express. */
    private fun gesturePaths(): JsonObject {
        fun point(time: Int) =
            JsonObject(
                mapOf(
                    "x" to JsonPrimitive(rnd.nextInt(GESTURE_SIZE)),
                    "y" to JsonPrimitive(rnd.nextInt(GESTURE_SIZE)),
                    "time" to JsonPrimitive(time),
                ),
            )
        val path = JsonArray(listOf(point(0), point(1 + rnd.nextInt(GESTURE_SIZE))))
        return JsonObject(mapOf("paths" to JsonArray(listOf(path))))
    }

    private fun conformingNumber(
        bound: Bound?,
        name: String,
    ): Double =
        if (name in WHOLE_NUMBER_PARAMS || rnd.nextBoolean()) {
            inRange(bound, name)
        } else {
            rnd.nextDouble(inRangeLow(bound), inRangeHigh(bound, name))
        }

    private fun conformingArray(
        tool: FuzzTool,
        name: String,
        schema: JsonObject,
    ): JsonElement {
        val items = schema["items"] as? JsonObject
        return JsonArray(
            List(1 + rnd.nextInt(ARRAY_MAX)) {
                if (items ==
                    null
                ) {
                    JsonPrimitive("a$it")
                } else {
                    conformingValue(tool, name, items)
                }
            },
        )
    }

    private fun conformingObject(schema: JsonObject): JsonElement {
        val props = schema["properties"] as? JsonObject
        if (props == null) {
            return JsonObject(
                buildMap { repeat(rnd.nextInt(ARRAY_MAX)) { put("k$it", JsonPrimitive("v$it")) } },
            )
        }
        val fake =
            FuzzTool(
                "nested",
                props.mapValues {
                    it.value.jsonObject
                },
                (schema["required"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty().toSet(),
            )
        return conforming(fake)
    }

    private fun conformingString(
        name: String,
        bound: Bound?,
    ): String =
        FIXED_STRINGS[name] ?: when {
            name == "url" || name == "uri" -> "https://example.com/${name[0]}${rnd.nextInt(MAX_SMALL)}"
            name == "path" -> "dir/file${rnd.nextInt(MAX_SMALL)}.txt"
            name.contains("package") -> "com.example.app"
            bound?.maxLength != null -> "t".repeat(1 + rnd.nextInt(bound.maxLength.coerceAtMost(SHORT_TEXT)))
            else -> identifier()
        }

    private fun identifier(): String {
        val length = 1 + rnd.nextInt(ID_LENGTH)
        return String(CharArray(length) { ID_ALPHABET[rnd.nextInt(ID_ALPHABET.length)] })
    }

    private fun inRangeLow(bound: Bound?): Double = bound?.min ?: DEFAULT_LOW

    private fun inRangeHigh(
        bound: Bound?,
        name: String,
    ): Double {
        val high = bound?.max ?: DEFAULT_HIGH
        return if (name in SLOW_PARAMS) minOf(high, inRangeLow(bound) + SLOW_SPAN) else high
    }

    private fun inRange(
        bound: Bound?,
        name: String,
    ): Double {
        val low = inRangeLow(bound)
        val high = inRangeHigh(bound, name)
        return (low + rnd.nextInt((high - low).toInt() + 1)).coerceAtMost(high)
    }

    /** A value for [param] that violates its [bound] by a distinctive margin, or null if it has no usable bound. */
    fun outOfBound(
        bound: Bound,
        isInteger: Boolean,
    ): Double? {
        val side = rnd.nextBoolean()
        val value =
            when {
                side && bound.max != null -> bound.max + SENTINEL_MARGIN + rnd.nextInt(SENTINEL_SPREAD)
                bound.min != null -> bound.min - SENTINEL_MARGIN - rnd.nextInt(SENTINEL_SPREAD)
                bound.max != null -> bound.max + SENTINEL_MARGIN + rnd.nextInt(SENTINEL_SPREAD)
                else -> return null
            }
        return if (isInteger) value.toLong().toDouble() else value + SENTINEL_FRACTION
    }

    /** A value of a JSON type different from [schemaType], or a hostile variant of the same type. */
    fun wrongTyped(): JsonElement = FuzzJson.value(rnd, WRONG_DEPTH)

    /** Hostile string: a marker, then control characters and fuzz text. */
    fun hostileString(marker: String): String {
        val control = CONTROLS[rnd.nextInt(CONTROLS.size)]
        return marker + control + FuzzStrings.random(rnd, HOSTILE_LENGTH)
    }

    fun hugeString(): String = "H".repeat(HUGE_MIN + rnd.nextInt(HUGE_SPREAD))

    fun hugeArray(): JsonElement {
        val size = HUGE_ARRAY_MIN + rnd.nextInt(HUGE_ARRAY_SPREAD)
        return JsonArray(List(size) { JsonPrimitive(it) })
    }

    fun deepNesting(): JsonElement {
        var value: JsonElement = JsonPrimitive(1)
        repeat(NESTING_MIN + rnd.nextInt(NESTING_SPREAD)) {
            value =
                if (it % 2 == 0) JsonArray(listOf(value)) else JsonObject(mapOf("a" to value))
        }
        return value
    }

    fun nullValue(): JsonElement = JsonNull

    companion object {
        /** Parameters whose value must come from a small vocabulary the tools validate (or the tests' mocks know). */
        private val FIXED_STRINGS =
            mapOf(
                "location_id" to "loc1",
                "node_id" to "node_root",
                "resolution" to "1280x720",
                "flash_mode" to "auto",
                "component" to "com.example/.Main",
                "cursor" to "1.1",
                "mime_type" to "text/plain",
                "type" to "activity",
                "notification_id" to "0a1b2c3d",
                "action_id" to "0a1b2c3d",
            )

        /** The storage location id the tests' mocked file provider accepts. */
        const val KNOWN_LOCATION = "loc1"

        /** The id of the only node of the tests' mocked accessibility tree. */
        const val KNOWN_NODE = "node_root"

        val CONTROLS =
            listOf(
                "\r",
                "\n",
                "\r\n",
                "\u0000",
                "\u001B",
                "\u0085",
                "\u2028",
                "\u2029",
                "\u007F",
                "\u000B",
                "\u000C",
                "\u001C",
            )

        /** Parameters the schema calls "number" but the tools only accept as whole numbers (milliseconds). */
        val WHOLE_NUMBER_PARAMS = setOf("duration", "time")

        /** Parameters whose conforming value is kept small because the tool waits that long (in milliseconds). */
        val SLOW_PARAMS = setOf("timeout", "duration_ms", "duration", "typing_speed")

        private val TYPING =
            listOf("android_type_append_text", "android_type_insert_text", "android_type_replace_text")

        val DOCUMENTED_BOUNDS: Map<String, Bound> =
            buildMap {
                put("android_scroll.variance", Bound(0.0, 20.0))
                put("android_tap_node.inset_percentage", Bound(0.0, 45.0))
                for (tool in TYPING) {
                    put("$tool.typing_speed", Bound(10.0, 5000.0))
                    put("$tool.typing_speed_variance", Bound(0.0, null))
                }
                put("android_type_append_text.text", Bound(null, null, 2000))
                put("android_type_insert_text.text", Bound(null, null, 2000))
                put("android_type_replace_text.new_text", Bound(null, null, 2000))
                put("android_type_replace_text.search", Bound(null, null, 10000))
                put("android_type_insert_text.offset", Bound(0.0, null))
                put("android_wait_for_node.timeout", Bound(1.0, 30000.0))
                put("android_wait_for_idle.timeout", Bound(1.0, 30000.0))
                put("android_wait_for_idle.match_percentage", Bound(0.0, 100.0))
                // list_files/read_file "limit" (max 200) is capped in FileOperationProviderImpl, mocked here.
                put("android_list_files.offset", Bound(0.0, null))
                put("android_read_file.offset", Bound(1.0, null))
                put("android_take_camera_photo.quality", Bound(1.0, 100.0))
                put("android_save_camera_photo.quality", Bound(1.0, 100.0))
                put("android_save_camera_video.duration", Bound(1.0, 30.0))
                put("android_notification_snooze.duration_ms", Bound(1.0, 604_800_000.0))
            }

        private const val GESTURE_SIZE = 500
        private const val OPTIONAL_ODDS = 3
        private const val ARRAY_MAX = 3
        private const val MAX_SMALL = 1000
        private const val SHORT_TEXT = 6
        private const val ID_LENGTH = 10
        private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789_"
        private const val DEFAULT_LOW = 0.0
        private const val DEFAULT_HIGH = 2000.0
        private const val SLOW_SPAN = 30.0
        private const val SENTINEL_MARGIN = 7919.0
        private const val SENTINEL_SPREAD = 100_000
        private const val SENTINEL_FRACTION = 0.5
        private const val WRONG_DEPTH = 3
        private const val HOSTILE_LENGTH = 16
        private const val HUGE_MIN = 20_000
        private const val HUGE_SPREAD = 60_000
        private const val HUGE_ARRAY_MIN = 2_000
        private const val HUGE_ARRAY_SPREAD = 4_000
        private const val NESTING_MIN = 30
        private const val NESTING_SPREAD = 120
    }
}
