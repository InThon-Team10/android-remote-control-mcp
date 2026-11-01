package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.openai.models.Reasoning
import com.openai.models.ReasoningEffort
import com.openai.models.responses.EasyInputMessage
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseIncludable
import com.openai.models.responses.ResponseInputItem
import com.openai.models.responses.Tool
import com.openai.models.responses.ToolChoiceOptions
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

/** Builds the Responses API requests: stateless (nothing stored server-side), the whole history sent as input. */
internal object OpenAiRequestFactory {
    const val SYSTEM_PROMPT = ElderGuidancePrompt.TEXT

    /** One non-strict function tool per MCP tool (order preserved), with the MCP tool's raw JSON schema. */
    fun functionTools(tools: List<McpTool>): List<FunctionTool> = tools.map(::functionTool)

    /** Never a class-derived schema: those need `java.lang.reflect.AnnotatedType`, which Android does not have. */
    fun functionTool(tool: McpTool): FunctionTool {
        val parameters = FunctionTool.Parameters.builder()
        McpToolSchemaMapper.parameters(tool).forEach { (key, value) ->
            parameters.putAdditionalProperty(key, JsonBridge.toJsonValue(value))
        }
        return FunctionTool
            .builder()
            .name(tool.name)
            .description(tool.description.orEmpty())
            .parameters(parameters.build())
            .strict(false)
            .build()
    }

    /**
     * One request over the whole [input] history, with low reasoning effort. `store=false` keeps nothing on the
     * server, so the encrypted reasoning is requested back to be replayed with the history. With tools, at most one
     * tool call per response: the helper guides one step at a time.
     */
    fun params(
        model: String,
        instructions: String,
        tools: List<FunctionTool>,
        input: List<ResponseInputItem>,
    ): ResponseCreateParams {
        val builder =
            ResponseCreateParams
                .builder()
                .model(model)
                .instructions(instructions)
                .inputOfResponse(input)
                .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                .store(false)
                .addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT)
        if (tools.isNotEmpty()) {
            builder
                .tools(tools.map(Tool::ofFunction))
                .toolChoice(ToolChoiceOptions.AUTO)
                .parallelToolCalls(false)
        }
        return builder.build()
    }

    fun userMessage(input: AgentInput): ResponseInputItem =
        when (input) {
            is AgentInput.Text -> {
                ResponseInputItem.ofEasyInputMessage(
                    EasyInputMessage
                        .builder()
                        .role(EasyInputMessage.Role.USER)
                        .content(input.text)
                        .build(),
                )
            }
        }
}
