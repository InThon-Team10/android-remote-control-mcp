package com.danielealbano.androidremotecontrolmcp.testutil

import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** The outcome of one `tools/call` POST: [problem] is set when the request itself failed or hung. */
internal class ToolResponse(
    val status: Int,
    val body: JsonObject?,
    val raw: String,
    val problem: String?,
)

/**
 * Applies the response properties of the tool fuzz suites to one answer and records each kind of violation once per
 * tool in [violations]. [judge] returns a short outcome label (`ok`, `isError`, `rpcError`, ...) for histograms.
 */
internal class ToolResponseJudge(
    private val violations: MutableMap<String, String>,
) {
    fun judge(
        call: FuzzCall,
        response: ToolResponse,
        where: String,
    ): String {
        val report = { kind: String, detail: String ->
            violations.putIfAbsent("${call.tool.name}|$kind", "[$kind] $detail :: $where")
            Unit
        }
        val body = response.body
        return when {
            response.problem != null -> {
                report("transport", response.problem)
                "problem"
            }

            response.status != HTTP_OK || body == null -> {
                report("status", "status=${response.status} body=${response.raw.take(PREVIEW)}")
                "status${response.status}"
            }

            body["error"] is JsonObject -> {
                rpcError(call, body.getValue("error") as JsonObject, report)
            }

            else -> {
                callResult(call, body, response.raw, report)
            }
        }
    }

    private fun rpcError(
        call: FuzzCall,
        error: JsonObject,
        report: (String, String) -> Unit,
    ): String {
        val code = error["code"]?.jsonPrimitive?.intOrNull
        val message = error["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (code != INVALID_PARAMS) report("error-code", "code=$code message=${message.take(PREVIEW)}")
        checkEcho(call, message, isErrorText = true, report)
        return "rpcError"
    }

    private fun callResult(
        call: FuzzCall,
        body: JsonObject,
        raw: String,
        report: (String, String) -> Unit,
    ): String {
        val result = body["result"] as? JsonObject
        val content = result?.get("content") as? JsonArray
        if (result == null || content == null) {
            report("shape", "neither CallToolResult nor error: ${raw.take(PREVIEW)}")
            return "badShape"
        }
        val isError = result["isError"]?.jsonPrimitive?.booleanOrNull == true
        val texts = content.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
        texts.forEach { checkEcho(call, it, isError, report) }
        val first = texts.firstOrNull()
        val needsWarning = !isError && call.tool.name in ToolClassification.DEVICE_CONTENT
        if (needsWarning && first?.startsWith(McpToolUtils.UNTRUSTED_CONTENT_WARNING) != true) {
            report("untrusted-warning", "device-content tool answered without the warning: ${first?.take(PREVIEW)}")
        }
        return if (isError) "isError" else "ok"
    }

    /**
     * Error texts must not echo any control character (McpToolException escapes them). A success text echoes only
     * values the real providers accepted, and they reject ASCII controls and DEL but not C1 or U+2028/U+2029, so
     * for success texts only the ASCII ones are checked (the rest is reported as a candidate).
     */
    private fun checkEcho(
        call: FuzzCall,
        text: String,
        isErrorText: Boolean,
        report: (String, String) -> Unit,
    ) {
        val control = call.injected?.first() ?: return
        if (!isErrorText && control.code > DEL) return
        if (text.contains(call.marker + control)) {
            val code = control.code.toString(HEX_RADIX)
            report("echo", "raw control character U+$code echoed: ${FuzzStrings.escape(text.take(PREVIEW))}")
        }
    }

    private companion object {
        const val HTTP_OK = 200
        const val INVALID_PARAMS = -32602
        const val PREVIEW = 300
        const val DEL = 0x7F
        const val HEX_RADIX = 16
    }
}
