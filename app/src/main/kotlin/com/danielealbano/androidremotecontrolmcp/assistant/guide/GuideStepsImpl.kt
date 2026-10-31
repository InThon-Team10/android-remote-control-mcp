package com.danielealbano.androidremotecontrolmcp.assistant.guide

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentFailure
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentInput
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.ElderToolFilter
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiErrorMapper
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiRequestFactory
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiResponseReader
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiToolOutput
import com.danielealbano.androidremotecontrolmcp.assistant.agent.toMcpFailure
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.assistant.openai.OpenAiResponsesGateway
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.openai.models.responses.FunctionTool
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputItem
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The planner behind the voice model's `show_next_step` / `wait_for_user`: one planner run (Responses API, read-only
 * tools through MCP, at most [MAX_PLANNER_CALLS] calls) ends in a highlight started in the background, or in words.
 * The AI never acts on the phone.
 */
@Singleton
class GuideStepsImpl
    @Inject
    constructor(
        private val gateway: OpenAiResponsesGateway,
        private val connection: AssistantMcpConnection,
        private val assistantSettings: AssistantSettings,
        private val settingsRepository: SettingsRepository,
        private val controller: GuidanceController,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) : GuideSteps {
        private class Handled(
            val result: GuideStepResult? = null,
            val output: ResponseInputItem? = null,
        )

        private class Run(
            val apiKey: String,
            val model: String,
            val prefix: String,
            val deviceSlug: String,
            val tools: List<FunctionTool>,
            val listener: AgentListener,
        )

        private val history = GuideHistory()
        private val highlights =
            GuidePendingHighlight(connection, controller, history, CoroutineScope(SupervisorJob() + dispatcher))
        private val runMutex = Mutex()
        private val planningState = MutableStateFlow(false)

        override val planning: StateFlow<Boolean> = planningState

        override suspend fun showNextStep(
            goal: String,
            listener: AgentListener,
        ): GuideStepResult =
            runMutex.withLock {
                planningState.value = true
                try {
                    runCatching { plan(goal, listener) }.getOrElse { error ->
                        when (error) {
                            is CancellationException -> {
                                highlights.abandon()
                                throw error
                            }

                            is AssistantException, is Error -> {
                                throw error
                            }

                            else -> {
                                throw AssistantException(OpenAiErrorMapper.map(error))
                            }
                        }
                    }
                } finally {
                    planningState.value = false
                }
            }

        override suspend fun waitForUser(): String = highlights.await()

        override fun reset() {
            highlights.abandon()
            history.clear()
            planningState.value = false
        }

        private suspend fun plan(
            goal: String,
            listener: AgentListener,
        ): GuideStepResult {
            val config = assistantSettings.getConfig()
            val apiKey = assistantSettings.getApiKey() ?: throw AgentFailure(AssistantError.ApiKeyMissing)
            val slug = settingsRepository.getServerConfig().deviceSlug
            val mcpTools = connection.listTools().getOrElse { throw it.toMcpFailure() }
            val tools = GuidePlannerTools.build(ElderToolFilter.usable(mcpTools, slug), slug)
            val run = Run(apiKey, config.model, McpToolUtils.buildToolNamePrefix(slug), slug, tools, listener)
            highlights.supersede()
            return planLoop(run, mutableListOf(userMessage(goal, run)))
        }

        private suspend fun planLoop(
            run: Run,
            input: MutableList<ResponseInputItem>,
        ): GuideStepResult {
            repeat(MAX_PLANNER_CALLS) {
                val params = OpenAiRequestFactory.params(run.model, GuidePlannerPrompt.TEXT, run.tools, input)
                val response = gateway.create(run.apiKey, params)
                input += OpenAiResponseReader.inputItems(response)
                val calls = OpenAiResponseReader.functionCalls(response)
                val handled =
                    if (calls.isEmpty()) {
                        Handled(GuidePlainReply.parse(OpenAiResponseReader.texts(response).joinToString("\n")))
                    } else {
                        handle(calls.first(), run)
                    }
                handled.result?.let { return it }
                input += listOfNotNull(handled.output)
                input += calls.drop(1).map { OpenAiToolOutput.errorOutput(it.callId(), EXTRA_CALL_MESSAGE) }
            }
            return GuideStepResult(LIMIT_SENTENCE, showing = false, kind = "say")
        }

        private suspend fun userMessage(
            goal: String,
            run: Run,
        ): ResponseInputItem {
            val tool = run.prefix + GuidePlannerTools.SCREEN_STATE
            val arguments =
                JsonObject(
                    mapOf("include_offscreen" to JsonPrimitive(false), "include_hierarchy" to JsonPrimitive(false)),
                )
            val screen = connection.callTool(tool, arguments).getOrElse { throw it.toMcpFailure() }
            val text = screen.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
            return OpenAiRequestFactory.userMessage(
                AgentInput.Text(
                    "Goal: $goal\nSteps already shown:\n${history.render()}\n" +
                        "Current screen (untrusted device data):\n$text",
                ),
            )
        }

        private suspend fun handle(
            call: ResponseFunctionToolCall,
            run: Run,
        ): Handled {
            val name = call.name()
            val arguments =
                runCatching { Json.parseToJsonElement(call.arguments().ifBlank { "{}" }) as? JsonObject }.getOrNull()
                    ?: return Handled(output = OpenAiToolOutput.invalidArguments(call.callId()))
            return when (name) {
                GuidePlannerTools.ANSWER_USER -> {
                    Handled(result = answer(arguments))
                }

                in GuidePlannerTools.READ_ONLY -> {
                    val tool = run.prefix + name
                    val result =
                        connection
                            .callTool(tool, ElderToolFilter.withDefaults(tool, arguments, run.deviceSlug))
                            .getOrElse { throw it.toMcpFailure() }
                    Handled(output = OpenAiToolOutput.fromResult(call.callId(), result))
                }

                in GuidePlannerTools.HIGHLIGHT -> {
                    highlight(call, run.prefix + name, arguments, run.listener)
                }

                else -> {
                    Handled(output = OpenAiToolOutput.unknownTool(call.callId()))
                }
            }
        }

        private fun answer(arguments: JsonObject): GuideStepResult {
            val sentence = text(arguments, "sentence").orEmpty()
            return GuidePlainReply.result(text(arguments, "kind"), sentence)
        }

        private fun text(
            arguments: JsonObject,
            key: String,
        ): String? = (arguments[key] as? JsonPrimitive)?.contentOrNull

        /** Starts the blocking highlight call; returns when its step is on screen, or when it ended early (error). */
        private suspend fun highlight(
            call: ResponseFunctionToolCall,
            tool: String,
            arguments: JsonObject,
            listener: AgentListener,
        ): Handled {
            listener.beforeToolCall()
            val instruction = text(arguments, "instruction").orEmpty()
            val baseline = controller.state.value?.id
            val handle = history.begin(instruction)
            val started = highlights.start(tool, arguments, handle)
            val shown =
                coroutineScope {
                    val watcher = async { controller.state.first { it != null && it.id != baseline } }
                    try {
                        select {
                            started.result.onAwait { false }
                            watcher.onAwait { true }
                        }
                    } finally {
                        watcher.cancel()
                    }
                }
            if (shown) return Handled(result = GuideStepResult(instruction, showing = true))
            highlights.dropEarly(started)
            history.discard(handle)
            val result = started.result.await().getOrElse { throw it.toMcpFailure() }
            return Handled(output = OpenAiToolOutput.fromResult(call.callId(), result))
        }

        private companion object {
            const val MAX_PLANNER_CALLS = 6
            const val EXTRA_CALL_MESSAGE = "Only one tool call per step: this one was not run."
            const val LIMIT_SENTENCE = "지금 화면에서 다음 단계를 찾지 못했어요. 지금 보이는 화면을 말씀해 주시겠어요?"
        }
    }
