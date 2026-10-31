package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.openai.OpenAiResponsesGateway
import com.openai.core.http.Headers
import com.openai.core.jsonMapper
import com.openai.models.ErrorObject
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import kotlinx.serialization.json.JsonPrimitive

/** Scripted [OpenAiResponsesGateway]: answers are consumed in order; every call is recorded. */
class FakeOpenAiResponsesGateway : OpenAiResponsesGateway {
    class Call(
        val apiKey: String,
        val params: ResponseCreateParams,
    ) {
        val input: List<ResponseInputItem> get() = params.input().get().asResponse()
    }

    val calls = mutableListOf<Call>()
    private val script = ArrayDeque<() -> Response>()

    fun enqueue(response: Response) = script.addLast { response }

    fun enqueueFailure(error: Throwable) = script.addLast { throw error }

    override suspend fun create(
        apiKey: String,
        params: ResponseCreateParams,
    ): Response {
        calls += Call(apiKey, params)
        return checkNotNull(script.removeFirstOrNull()) { "No scripted OpenAI response left" }.invoke()
    }
}

/** Responses API payloads parsed by the SDK's own JSON mapper, as they arrive from the service. */
object OpenAiTestResponses {
    const val ENCRYPTED_REASONING = "encrypted-reasoning"

    private fun quote(value: String) = JsonPrimitive(value).toString()

    /** [status] null leaves the field out; [extra] is appended to the top-level object (leading comma included). */
    fun response(
        vararg items: String,
        status: String? = "completed",
        extra: String = "",
    ): Response {
        val statusField = status?.let { """"status":"$it",""" }.orEmpty()
        return jsonMapper().readValue(
            """{"id":"resp_1","object":"response","created_at":0,"model":"m",$statusField""" +
                """"output":[${items.joinToString(",")}],"parallel_tool_calls":false,"tool_choice":"auto",""" +
                """"tools":[]$extra}""",
            Response::class.java,
        )
    }

    fun reasoning(id: String = "rs_1") =
        """{"type":"reasoning","id":"$id","summary":[],""" +
            """"encrypted_content":"$ENCRYPTED_REASONING"}"""

    fun message(text: String) =
        """{"type":"message","id":"msg_1","role":"assistant","status":"completed",""" +
            """"content":[{"type":"output_text","text":${quote(text)},"annotations":[]}]}"""

    fun refusal(text: String) =
        """{"type":"message","id":"msg_1","role":"assistant","status":"completed",""" +
            """"content":[{"type":"refusal","refusal":${quote(text)}}]}"""

    fun call(
        name: String,
        arguments: String = "{}",
        callId: String = "call_1",
    ) = """{"type":"function_call","id":"fc_$callId","call_id":"$callId","name":"$name",""" +
        """"arguments":${quote(arguments)},"status":"completed"}"""

    fun text(text: String): Response = response(reasoning(), message(text))

    /** One response with a reasoning item and one call per pair (name to JSON arguments), call ids `call_1`... */
    fun calls(vararg calls: Pair<String, String>): Response =
        response(
            reasoning(),
            *calls.mapIndexed { index, (name, arguments) -> call(name, arguments, "call_${index + 1}") }.toTypedArray(),
        )

    fun headers(): Headers = Headers.builder().build()

    fun errorObject(code: String?): ErrorObject =
        ErrorObject
            .builder()
            .code(code)
            .message("server message that may echo the prompt")
            .param(null)
            .type("invalid_request_error")
            .build()
}
