package com.danielealbano.androidremotecontrolmcp.testutil

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random

/** One generated HTTP request. [validAuth] is true only when the Authorization header carries the token. */
internal class FuzzRequest(
    val method: String,
    val path: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
    val validAuth: Boolean,
    val label: String,
) {
    override fun toString(): String =
        "$method ${FuzzStrings.escape(path)} " +
            "headers=${headers.map { it.first + "=" + FuzzStrings.escape(it.second.take(HEADER_PREVIEW)) }} " +
            "bodyBytes=${body.size} body=${FuzzStrings.escape(String(body, Charsets.ISO_8859_1).take(BODY_PREVIEW))} " +
            "label=$label"

    private companion object {
        const val HEADER_PREVIEW = 80
        const val BODY_PREVIEW = 200
    }
}

/** Seeded generator of hostile HTTP requests aimed at the MCP routing and JSON-RPC layer. */
internal class FuzzHttpRequests(
    private val rnd: Random,
    private val token: String,
    private val toolNames: List<String>,
) {
    fun next(): FuzzRequest {
        val auth = authHeader()
        val headers = mutableListOf<Pair<String, String>>()
        auth.header?.let { headers.add("Authorization" to it) }
        if (rnd.nextInt(DUPLICATE_AUTH_ODDS) == 0) headers.add("Authorization" to randomAuthValue().first)
        headers.addAll(contentHeaders())
        repeat(rnd.nextInt(EXTRA_HEADERS_MAX)) { headers.add(extraHeader()) }
        val method = pickMethod()
        val sendsBody = method == "POST" || rnd.nextInt(BODY_ON_OTHER_ODDS) == 0
        val (body, label) = if (sendsBody) body() else ByteArray(0) to "none"
        return FuzzRequest(method, path(), headers, body, auth.valid, label)
    }

    /** A valid tool call to a harmless tool, used for the unauthenticated-reach probes. */
    fun probeBody(): ByteArray =
        JsonObject(
            mapOf(
                "jsonrpc" to JsonPrimitive("2.0"),
                "id" to JsonPrimitive(1),
                "method" to JsonPrimitive("tools/call"),
                "params" to
                    JsonObject(
                        mapOf(
                            "name" to JsonPrimitive("android_press_home"),
                            "arguments" to JsonObject(emptyMap()),
                        ),
                    ),
            ),
        ).toString().toByteArray()

    private class Auth(
        val header: String?,
        val valid: Boolean,
    )

    private fun pickMethod(): String = if (rnd.nextInt(POST_WEIGHT) != 0) "POST" else METHODS[rnd.nextInt(METHODS.size)]

    private fun authHeader(): Auth =
        when (rnd.nextInt(AUTH_KINDS)) {
            0, 1, 2 -> Auth("Bearer $token", true)
            3 -> Auth(null, false)
            else -> randomAuthValue().let { Auth(it.first, it.second) }
        }

    private fun randomAuthValue(): Pair<String, Boolean> {
        val valid = AUTH_VALUES[rnd.nextInt(AUTH_VALUES.size)]
        return valid?.let { it(token) to true } ?: authNoise()
    }

    /** Header values that must NOT authenticate. */
    private fun authNoise(): Pair<String, Boolean> =
        when (rnd.nextInt(NOISE_KINDS)) {
            0 -> "Bearer " + "A".repeat(rnd.nextInt(HUGE_HEADER))
            1 -> "Bearer ${headerNoise()}"
            else -> REJECTED_AUTH[rnd.nextInt(REJECTED_AUTH.size)](token)
        } to false

    private fun contentHeaders(): List<Pair<String, String>> {
        val contentType = CONTENT_TYPES[rnd.nextInt(CONTENT_TYPES.size)]?.invoke(headerNoise())
        val accept = ACCEPTS[rnd.nextInt(ACCEPTS.size)]?.invoke(rnd.nextInt(HUGE_ACCEPT))
        return listOfNotNull(contentType?.let { "Content-Type" to it }, accept?.let { "Accept" to it })
    }

    /** Fuzz text the HTTP client can send: printable ASCII, one in ten left raw (the client may refuse it). */
    private fun headerNoise(): String {
        val raw = FuzzStrings.random(rnd, TOKEN_NOISE)
        return if (rnd.nextInt(RAW_NOISE_ODDS) == 0) raw else raw.filter { it.code in HEADER_SAFE_RANGE }
    }

    private fun extraHeader(): Pair<String, String> {
        val name = EXTRA_NAMES[rnd.nextInt(EXTRA_NAMES.size)]
        val value =
            when (rnd.nextInt(EXTRA_VALUE_KINDS)) {
                0 -> "v".repeat(rnd.nextInt(HUGE_HEADER))
                1 -> "a\r\nX-Injected: 1"
                2 -> "a\nb"
                3 -> headerNoise()
                4 -> ""
                else -> "evil.example:${rnd.nextInt(MAX_PORT)}"
            }
        return name to value
    }

    private fun path(): String =
        when (rnd.nextInt(PATH_KINDS)) {
            0, 1, 2, 3, 4, 5 -> "/mcp"
            6, 7, 8 -> ODD_PATHS[rnd.nextInt(ODD_PATHS.size)]
            9 -> "/mcp" + FuzzStrings.random(rnd, TOKEN_NOISE).filter { it.code in PATH_SAFE_RANGE }
            else -> "/" + List(rnd.nextInt(PATH_SEGMENTS)) { SEGMENTS[rnd.nextInt(SEGMENTS.size)] }.joinToString("/")
        }

    private fun body(): Pair<ByteArray, String> =
        when (rnd.nextInt(BODY_KINDS)) {
            0, 1, 2, 3, 4 -> {
                envelope().toByteArray() to "envelope"
            }

            5 -> {
                val full = envelope()
                full.take(rnd.nextInt(full.length + 1)).toByteArray() to "truncated"
            }

            6 -> {
                ByteArray(rnd.nextInt(GARBAGE_MAX)).also { rnd.nextBytes(it) } to "garbage"
            }

            7 -> {
                ("" + envelope()).toByteArray() to "bom"
            }

            8 -> {
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + envelope().toByteArray() to "bom-bytes"
            }

            9 -> {
                deepBody()
            }

            10 -> {
                duplicateKeys().toByteArray() to "duplicate-keys"
            }

            11 -> {
                batch().toByteArray() to "batch"
            }

            12 -> {
                envelope().toByteArray(Charsets.UTF_16LE) to "utf16"
            }

            else -> {
                FuzzStrings.random(rnd, TOKEN_NOISE).toByteArray() to "random-text"
            }
        }

    private fun deepBody(): Pair<ByteArray, String> {
        val depth = rnd.nextInt(1, DEEP_MAX)
        val inner = FuzzJson.nestedText(depth, rnd.nextBoolean())
        val call =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":" +
                "{\"name\":\"android_tap\",\"arguments\":"
        val text =
            when (rnd.nextInt(DEEP_WRAP_KINDS)) {
                0 -> inner
                1 -> "$call$inner}}"
                else -> "{\"jsonrpc\":\"2.0\",\"id\":$inner,\"method\":\"ping\"}"
            }
        return text.toByteArray() to "deep($depth)"
    }

    private fun duplicateKeys(): String {
        val method = METHOD_NAMES[rnd.nextInt(METHOD_NAMES.size)]
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"id\":\"two\",\"method\":\"$method\",\"method\":\"tools/list\"," +
            "\"params\":{},\"params\":{\"name\":\"android_press_home\"}}"
    }

    private fun batch(): String {
        val items =
            List(rnd.nextInt(BATCH_MAX)) {
                if (rnd.nextInt(MIXED_ODDS) == 0) FuzzJson.primitive(rnd).toString() else envelope()
            }
        return items.joinToString(",", "[", "]")
    }

    private fun envelope(): String {
        val fields = linkedMapOf<String, JsonElement>()
        jsonrpc()?.let { fields["jsonrpc"] = it }
        id()?.let { fields["id"] = it }
        method()?.let { fields["method"] = it }
        params(fields["method"])?.let { fields["params"] = it }
        if (rnd.nextInt(EXTRA_FIELD_ODDS) == 0) fields[FuzzStrings.random(rnd, KEY_NOISE)] = FuzzJson.value(rnd, 2)
        val text = JsonObject(fields).toString()
        return if (rnd.nextInt(HUGE_ID_ODDS) == 0) {
            text.replaceFirst("\"id\":", "\"id\":" + "9".repeat(rnd.nextInt(HUGE_NUMBER)))
        } else {
            text
        }
    }

    private fun jsonrpc(): JsonElement? =
        when (rnd.nextInt(JSONRPC_KINDS)) {
            0, 1, 2, 3, 4, 5 -> JsonPrimitive("2.0")
            6 -> JsonPrimitive("1.0")
            7 -> JsonPrimitive(2.0)
            8 -> JsonNull
            9 -> JsonPrimitive(true)
            10 -> JsonArray(emptyList())
            else -> null
        }

    private fun id(): JsonElement? =
        when (rnd.nextInt(ID_KINDS)) {
            0, 1, 2 -> JsonPrimitive(rnd.nextInt(MAX_ID))
            3 -> JsonPrimitive(Long.MAX_VALUE)
            4 -> JsonPrimitive(-rnd.nextInt(MAX_ID))
            5 -> JsonPrimitive(FuzzStrings.random(rnd, KEY_NOISE))
            6 -> JsonPrimitive("i".repeat(rnd.nextInt(HUGE_ID)))
            7 -> JsonNull
            8 -> JsonPrimitive(rnd.nextDouble())
            9 -> JsonPrimitive(rnd.nextBoolean())
            10 -> FuzzJson.value(rnd, 2)
            else -> null
        }

    private fun method(): JsonElement? =
        when (rnd.nextInt(METHOD_KINDS)) {
            0, 1, 2, 3, 4, 5, 6 -> JsonPrimitive(METHOD_NAMES[rnd.nextInt(METHOD_NAMES.size)])
            7 -> JsonPrimitive("tools/call")
            8 -> JsonPrimitive(FuzzStrings.random(rnd, KEY_NOISE))
            9 -> JsonPrimitive(rnd.nextInt())
            10 -> JsonNull
            11 -> FuzzJson.value(rnd, 2)
            else -> null
        }

    private fun params(method: JsonElement?): JsonElement? {
        val isCall = (method as? JsonPrimitive)?.content == "tools/call"
        return when (rnd.nextInt(PARAMS_KINDS)) {
            0, 1, 2, 3 -> if (isCall) toolCallParams() else JsonObject(emptyMap())
            4 -> JsonObject(emptyMap())
            5 -> JsonNull
            6 -> JsonArray(emptyList())
            7 -> JsonPrimitive(FuzzStrings.random(rnd, KEY_NOISE))
            8 -> FuzzJson.value(rnd, MAX_VALUE_DEPTH)
            9 -> null
            else -> toolCallParams()
        }
    }

    private fun toolCallParams(): JsonElement {
        val fields = linkedMapOf<String, JsonElement>()
        when (rnd.nextInt(NAME_KINDS)) {
            0, 1, 2, 3 -> fields["name"] = JsonPrimitive(toolNames[rnd.nextInt(toolNames.size)])
            4 -> fields["name"] = JsonPrimitive(FuzzStrings.random(rnd, KEY_NOISE))
            5 -> fields["name"] = FuzzJson.value(rnd, 1)
            else -> Unit
        }
        when (rnd.nextInt(ARGS_KINDS)) {
            0, 1 -> fields["arguments"] = JsonObject(emptyMap())
            2 -> fields["arguments"] = FuzzJson.value(rnd, MAX_VALUE_DEPTH)
            3 -> fields["arguments"] = JsonNull
            4 -> fields["arguments"] = JsonArray(listOf(JsonPrimitive(1)))
            else -> Unit
        }
        if (rnd.nextInt(META_ODDS) == 0) fields["_meta"] = FuzzJson.value(rnd, 2)
        return JsonObject(fields)
    }

    private companion object {
        /** Header values that authenticate, as functions of the token; null entries are replaced by a rejected form. */
        val AUTH_VALUES: List<((String) -> String)?> =
            listOf(
                { t -> "bearer $t" },
                { t -> "BEARER   $t  " },
                null,
                null,
                null,
            )
        val REJECTED_AUTH: List<(String) -> String> =
            listOf(
                { t -> "Bearer ${t.dropLast(1)}" },
                { t -> "Bearer ${t}x" },
                { t -> "Bearer ${t.uppercase()}" },
                { t -> "Bearer $t\u0000" },
                { _ -> "Bearer" },
                { _ -> "Bearer " },
                { t -> "Basic $t" },
                { t -> t },
                { t -> "Bearer Bearer $t" },
                { t -> "Bearer\t$t" },
                { t -> "Bearer ${t.reversed()}" },
            )
        val CONTENT_TYPES: List<((String) -> String)?> =
            listOf(
                { _ -> "application/json" },
                { _ -> "application/json" },
                { _ -> "application/json" },
                { _ -> "application/json; charset=utf-16" },
                { _ -> "text/plain" },
                { _ -> "a/b" },
                { _ -> "" },
                { _ -> "multipart/form-data" },
                { noise -> "application/json" + noise },
                { _ -> "application/x-www-form-urlencoded" },
                null,
                null,
            )
        val ACCEPTS: List<((Int) -> String)?> =
            listOf(
                { _ -> "application/json, text/event-stream" },
                { _ -> "application/json, text/event-stream" },
                { _ -> "application/json, text/event-stream" },
                { _ -> "application/json" },
                { _ -> "text/event-stream" },
                { _ -> "*/*" },
                { _ -> "text/html" },
                { _ -> ";;;,,,q=x" },
                { n -> "application/json".repeat(n) },
                null,
            )
        val METHODS = listOf("POST", "GET", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "FOO", "TRACE")
        val METHOD_NAMES =
            listOf(
                "initialize",
                "tools/list",
                "tools/call",
                "ping",
                "notifications/initialized",
                "notifications/cancelled",
                "notifications/progress",
                "resources/list",
                "resources/read",
                "prompts/list",
                "prompts/get",
                "completion/complete",
                "logging/setLevel",
                "sampling/createMessage",
                "roots/list",
                "unknown/method",
                "",
            )
        val EXTRA_NAMES =
            listOf(
                "X-Forwarded-For",
                "X-Forwarded-Host",
                "X-Forwarded-Proto",
                "Origin",
                "Mcp-Session-Id",
                "Mcp-Protocol-Version",
                "Last-Event-ID",
                "Cookie",
                "Range",
                "X-Custom",
            )
        val ODD_PATHS =
            listOf(
                "//mcp",
                "/mcp/",
                "/MCP",
                "/mcp/.",
                "/./mcp",
                "/.well-known/../mcp",
                "/.well-known/%2e%2e/mcp",
                "/.well-known/..%2fmcp",
                "/s/../mcp",
                "/s/%2e%2e/mcp",
                "/s/..%2fmcp",
                "/health/../mcp",
                "/health%2f..%2fmcp",
                "/%6dcp",
                "/mcp%2f",
                "/mcp%00",
                "/mcp%2e",
                "/mcp?x=1",
                "/mcp;x=y",
                "/mcp#frag",
                "/mcp?",
                "/a/../mcp",
                "/a/../../mcp",
                "/%2e%2e/mcp",
                "/health",
                "/health/",
                "/HEALTH",
                "/%68ealth",
                "/health?x=1",
                "/health;a=b",
                "/.well-known/oauth-protected-resource/mcp",
                "/.well-known/",
                "/s/abc",
                "/s/",
                "/register",
                "/token",
                "/authorize",
                "/authorize/status",
                "/",
                "/%",
                "/%zz",
                "/mcp%",
                "/é",
            )
        val SEGMENTS =
            listOf("mcp", "..", ".", "health", ".well-known", "s", "%2e%2e", "%2e", "", "x", "%2f", "%5c", "..%5c")
        val PATH_SAFE_RANGE = 0x21..0x7E
        val HEADER_SAFE_RANGE = 0x20..0x7E
        const val RAW_NOISE_ODDS = 10
        const val POST_WEIGHT = 3
        const val DUPLICATE_AUTH_ODDS = 12
        const val EXTRA_HEADERS_MAX = 3
        const val BODY_ON_OTHER_ODDS = 3
        const val AUTH_KINDS = 7
        const val NOISE_KINDS = 4
        const val HUGE_HEADER = 70_000
        const val TOKEN_NOISE = 40
        const val KEY_NOISE = 12
        const val HUGE_ACCEPT = 2_000
        const val EXTRA_VALUE_KINDS = 14
        const val MAX_PORT = 70_000
        const val PATH_KINDS = 14
        const val PATH_SEGMENTS = 5
        const val BODY_KINDS = 14
        const val GARBAGE_MAX = 512
        const val DEEP_MAX = 400
        const val DEEP_WRAP_KINDS = 3
        const val BATCH_MAX = 6
        const val MIXED_ODDS = 4
        const val EXTRA_FIELD_ODDS = 8
        const val HUGE_ID_ODDS = 30
        const val HUGE_NUMBER = 400
        const val JSONRPC_KINDS = 12
        const val ID_KINDS = 12
        const val MAX_ID = 100_000
        const val HUGE_ID = 100_000
        const val METHOD_KINDS = 13
        const val PARAMS_KINDS = 11
        const val MAX_VALUE_DEPTH = 4
        const val NAME_KINDS = 7
        const val ARGS_KINDS = 6
        const val META_ODDS = 10
    }
}
