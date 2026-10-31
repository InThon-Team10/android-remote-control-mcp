package com.danielealbano.androidremotecontrolmcp.assistant.guide

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Guard for a planner that answered in plain text instead of calling `answer_user`: it counts as an answer. */
internal object GuidePlainReply {
    private const val DONE_PREFIX = "done:"

    fun parse(text: String): GuideStepResult {
        val trimmed = text.trim()
        val fromJson = parseJson(trimmed)
        return when {
            fromJson != null -> fromJson
            trimmed.startsWith(DONE_PREFIX, ignoreCase = true) -> result("done", trimmed.drop(DONE_PREFIX.length))
            else -> result("say", trimmed)
        }
    }

    private fun parseJson(text: String): GuideStepResult? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { Json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject }.getOrNull()
        val sentence = obj?.get("sentence")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        return sentence?.let {
            result(obj.get("kind")?.let { k -> runCatching { k.jsonPrimitive.contentOrNull }.getOrNull() }, it)
        }
    }

    fun result(
        kind: String?,
        sentence: String,
    ): GuideStepResult =
        GuideStepResult(
            say = sentence.trim(),
            showing = false,
            kind = kind?.takeIf { it in GuidePlannerTools.KINDS } ?: "say",
        )
}
