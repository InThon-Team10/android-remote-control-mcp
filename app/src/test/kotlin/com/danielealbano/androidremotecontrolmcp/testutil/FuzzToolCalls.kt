package com.danielealbano.androidremotecontrolmcp.testutil

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/** A value that breaks a parameter's contract and must therefore never reach a mocked service. */
internal sealed interface Sentinel {
    val param: String

    class Number(
        override val param: String,
        val value: Double,
    ) : Sentinel

    class Text(
        override val param: String,
        val text: String,
    ) : Sentinel

    class Length(
        override val param: String,
        val maxLength: Int,
    ) : Sentinel
}

/**
 * One generated tool call. [marker] prefixes every hostile string and [injected] is the control character put right
 * after it, so an echo of the raw value can be recognised in the answer.
 */
internal class FuzzCall(
    val tool: FuzzTool,
    val arguments: JsonElement,
    val sentinels: List<Sentinel>,
    val marker: String,
    val injected: String?,
    val description: String,
)

/** Builds conforming, boundary and mutated [FuzzCall]s from tool schemas. */
internal class FuzzToolCalls(
    private val rnd: Random,
) {
    private val gen = FuzzToolArguments(rnd)

    fun conformingArguments(tool: FuzzTool): JsonObject = gen.conforming(tool)

    fun conforming(
        tool: FuzzTool,
        iteration: Int,
    ): FuzzCall {
        val args = gen.conforming(tool)
        return FuzzCall(tool, args, emptyList(), marker(iteration), null, "args=${preview(args)}")
    }

    /** For each parameter with a bound, length limit or enum: [perParam] calls, conforming except that one value. */
    fun boundary(
        tool: FuzzTool,
        perParam: Int,
    ): List<FuzzCall> =
        tool.properties.flatMap { (param, schema) ->
            List(perParam) { iteration ->
                val base = gen.conforming(tool).toMutableMap()
                val sentinel = boundarySentinel(tool, param, schema, base, iteration)
                val note = "boundary($param) args=${preview(JsonObject(base))}"
                FuzzCall(tool, JsonObject(base), listOfNotNull(sentinel), marker(iteration), null, note)
            }.filter { it.sentinels.isNotEmpty() }
        }

    private fun boundarySentinel(
        tool: FuzzTool,
        param: String,
        schema: JsonObject,
        base: MutableMap<String, JsonElement>,
        iteration: Int,
    ): Sentinel? {
        val type = schema["type"]?.jsonPrimitive?.contentOrNull
        val bound = gen.boundOf(tool, param)
        return when {
            schema["enum"] is JsonArray -> {
                val tag = "zq_enum_$iteration"
                base[param] = JsonPrimitive(tag)
                Sentinel.Text(param, tag)
            }

            bound?.maxLength != null -> {
                base[param] = JsonPrimitive("L".repeat(bound.maxLength + 1 + iteration))
                Sentinel.Length(param, bound.maxLength)
            }

            bound != null && (type == "integer" || type == "number") -> {
                outOfBoundNumber(param, type, bound, base)
            }

            else -> {
                null
            }
        }
    }

    private fun outOfBoundNumber(
        param: String,
        type: String,
        bound: Bound,
        base: MutableMap<String, JsonElement>,
    ): Sentinel? {
        val value = gen.outOfBound(bound, type == "integer") ?: return null
        base[param] = JsonPrimitive(if (type == "integer") value.toLong() else value)
        return Sentinel.Number(param, value)
    }

    /** Conforming arguments with one to [MAX_MUTATIONS] hostile changes (or, rarely, a non-object `arguments`). */
    fun mutated(
        tool: FuzzTool,
        iteration: Int,
    ): FuzzCall {
        val state = MutationState(gen.conforming(tool).toMutableMap(), marker(iteration))
        val names = tool.properties.keys.toList()
        repeat(1 + rnd.nextInt(MAX_MUTATIONS)) {
            val param = if (names.isEmpty()) "x" else names[rnd.nextInt(names.size)]
            mutate(tool, param, state)
        }
        val replaced = rnd.nextInt(WHOLE_ODDS) == 0
        val arguments: JsonElement = if (replaced) gen.wrongTyped() else JsonObject(state.base)
        val sentinels = if (replaced) emptyList() else state.sentinels
        val whole = if (replaced) " WHOLE-ARGUMENTS-REPLACED" else ""
        val note = state.notes.joinToString() + whole + " args=" + preview(arguments)
        return FuzzCall(tool, arguments, sentinels, state.marker, state.injected, note)
    }

    private class MutationState(
        val base: MutableMap<String, JsonElement>,
        val marker: String,
    ) {
        val sentinels = mutableListOf<Sentinel>()
        val notes = mutableListOf<String>()
        var injected: String? = null
    }

    private fun mutate(
        tool: FuzzTool,
        param: String,
        state: MutationState,
    ) {
        val schema = tool.properties[param]
        val type = schema?.get("type")?.jsonPrimitive?.contentOrNull
        val kind = rnd.nextInt(MUTATION_KINDS)
        val note =
            if (kind < BASIC_KINDS) {
                basicMutation(kind, param, type, state)
            } else {
                structuralMutation(kind, tool, param, schema, state)
            }
        state.notes.add("$note($param)")
    }

    private fun basicMutation(
        kind: Int,
        param: String,
        type: String?,
        state: MutationState,
    ): String =
        when (kind) {
            0 -> "drop".also { state.base.remove(param) }
            1 -> "wrongType".also { wrongType(param, type, state) }
            2 -> "null".also { state.base[param] = JsonNull }
            3 -> "nanLike".also { state.base[param] = JsonPrimitive(NAN_LIKE[rnd.nextInt(NAN_LIKE.size)]) }
            else -> "extraKey".also { state.base["unknown_${rnd.nextInt(MAX_TAG)}"] = gen.wrongTyped() }
        }

    private fun structuralMutation(
        kind: Int,
        tool: FuzzTool,
        param: String,
        schema: JsonObject?,
        state: MutationState,
    ): String =
        when (kind) {
            5 -> outOfRange(tool, param, schema?.get("type")?.jsonPrimitive?.contentOrNull, state)
            6 -> "hugeString".also { state.base[param] = JsonPrimitive(gen.hugeString()) }
            7 -> "hugeArray".also { state.base[param] = gen.hugeArray() }
            8 -> "deep".also { state.base[param] = gen.deepNesting() }
            9 -> "hostile".also { hostile(param, state) }
            else -> invalidString(tool, param, schema, state)
        }

    private fun wrongType(
        param: String,
        type: String?,
        state: MutationState,
    ) {
        val tag = "zq_${rnd.nextInt(MAX_TAG)}"
        state.base[param] = JsonPrimitive(tag)
        if (type != "string") state.sentinels.add(Sentinel.Text(param, tag))
    }

    private fun hostile(
        param: String,
        state: MutationState,
    ) {
        val text = gen.hostileString(state.marker)
        state.injected = text.substring(state.marker.length, state.marker.length + 1)
        state.base[param] = JsonPrimitive(text)
    }

    private fun outOfRange(
        tool: FuzzTool,
        param: String,
        type: String?,
        state: MutationState,
    ): String {
        val bound = gen.boundOf(tool, param)
        val numeric = type == "integer" || type == "number"
        val value = if (bound != null && numeric) gen.outOfBound(bound, type == "integer") else null
        if (value == null) {
            state.base[param] = gen.wrongTyped()
            return "wrongTyped"
        }
        state.base[param] = JsonPrimitive(if (type == "integer") value.toLong() else value)
        state.sentinels.add(Sentinel.Number(param, value))
        return "outOfRange=$value"
    }

    private fun invalidString(
        tool: FuzzTool,
        param: String,
        schema: JsonObject?,
        state: MutationState,
    ): String {
        val bound = gen.boundOf(tool, param)
        return when {
            schema?.get("enum") is JsonArray -> {
                val tag = "zq_enum_${rnd.nextInt(MAX_TAG)}"
                state.base[param] = JsonPrimitive(tag)
                state.sentinels.add(Sentinel.Text(param, tag))
                "badEnum"
            }

            bound?.maxLength != null -> {
                state.base[param] = JsonPrimitive("L".repeat(bound.maxLength + 1 + rnd.nextInt(LENGTH_SPREAD)))
                state.sentinels.add(Sentinel.Length(param, bound.maxLength))
                "tooLong"
            }

            else -> {
                state.base[param] = JsonPrimitive(FuzzStrings.random(rnd, SHORT))
                "fuzzString"
            }
        }
    }

    private fun marker(iteration: Int) = "FZ${iteration}q"

    private fun preview(element: JsonElement) = FuzzStrings.escape(element.toString().take(PREVIEW))

    private companion object {
        val NAN_LIKE = listOf("NaN", "Infinity", "-Infinity", "1e999", "-", "", " ", "0x10", "1,5", "--1")
        const val MAX_MUTATIONS = 3
        const val MUTATION_KINDS = 11
        const val BASIC_KINDS = 5
        const val MAX_TAG = 1_000_000
        const val WHOLE_ODDS = 25
        const val LENGTH_SPREAD = 500
        const val SHORT = 24
        const val PREVIEW = 300
    }
}
