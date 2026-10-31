package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.google.genai.types.AutomaticFunctionCallingConfig
import com.google.genai.types.Content
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.ThinkingLevel
import com.google.genai.types.ToolConfig
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

/** Builds the per-run request configuration and the user turn. */
internal object GeminiRequestFactory {
    const val USER_ROLE = "user"

    const val SYSTEM_PROMPT = ElderGuidancePrompt.TEXT

    fun config(tools: List<McpTool>): GenerateContentConfig {
        val builder =
            GenerateContentConfig
                .builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .thinkingConfig(ThinkingConfig.builder().thinkingLevel(ThinkingLevel.Known.LOW).build())
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build())
        if (tools.isNotEmpty()) {
            builder
                .tools(listOf(McpToolSchemaMapper.toGeminiTool(tools)))
                .toolConfig(
                    ToolConfig
                        .builder()
                        .functionCallingConfig(
                            FunctionCallingConfig.builder().mode(FunctionCallingConfigMode.Known.AUTO).build(),
                        ).build(),
                )
        }
        return builder.build()
    }

    fun userContent(input: AgentInput): Content =
        when (input) {
            is AgentInput.Text -> {
                Content
                    .builder()
                    .role(USER_ROLE)
                    .parts(listOf(Part.fromText(input.text)))
                    .build()
            }
        }
}
