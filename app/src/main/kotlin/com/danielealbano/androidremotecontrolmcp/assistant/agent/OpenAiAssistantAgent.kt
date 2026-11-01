package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.assistant.openai.OpenAiResponsesGateway
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * OpenAI implementation of [AssistantAgent]: our own function-calling loop on the Responses API over the app's MCP
 * tools. Nothing is stored server-side, so the history (input and output items, reasoning included) is replayed
 * with every request.
 */
@Singleton
class OpenAiAssistantAgent
    @Inject
    constructor(
        private val gateway: OpenAiResponsesGateway,
        private val connection: AssistantMcpConnection,
        private val assistantSettings: AssistantSettings,
        private val settingsRepository: SettingsRepository,
        private val toolRunner: OpenAiToolCallRunner,
    ) : AssistantAgent {
        private val runMutex = Mutex()
        private val lock = Any()
        private val history = mutableListOf<ResponseInputItem>()

        /** A non-Completed outcome (limit, failure) or cancellation drops the whole turn from the history. */
        override suspend fun run(
            input: AgentInput,
            listener: AgentListener,
        ): AgentOutcome =
            runMutex.withLock {
                val mark = locked { history.size }
                val outcome =
                    runCatching { execute(input, listener) }.getOrElse { error ->
                        locked { truncate(mark) }
                        if (error is CancellationException || error is Error) throw error
                        AgentOutcome.Failed(OpenAiErrorMapper.map(error))
                    }
                if (outcome !is AgentOutcome.Completed) locked { truncate(mark) }
                outcome
            }

        override fun reset() {
            locked { history.clear() }
        }

        private suspend fun execute(
            input: AgentInput,
            listener: AgentListener,
        ): AgentOutcome {
            val settings = assistantSettings.getConfig()
            checkAllowed(settings)
            val apiKey = assistantSettings.getApiKey() ?: throw AgentFailure(AssistantError.ApiKeyMissing)
            val deviceSlug = settingsRepository.getServerConfig().deviceSlug
            val tools = ElderToolFilter.usable(connection.listTools().getOrElse { throw it.toMcpFailure() }, deviceSlug)
            val context =
                OpenAiRunContext(
                    apiKey = apiKey,
                    model = settings.model,
                    tools = OpenAiRequestFactory.functionTools(tools) + GuidanceReply.tool,
                    stepLimit = settings.stepLimit,
                    deviceSlug = deviceSlug,
                    toolNames = tools.mapTo(HashSet()) { it.name },
                )
            locked { history.add(OpenAiRequestFactory.userMessage(input)) }
            var steps = 0
            while (true) {
                val outcome = step(context, listener, steps)
                if (outcome != null) return outcome
                steps++
            }
        }

        private fun checkAllowed(settings: AssistantConfig) {
            if (!settings.enabled) throw AgentFailure(AssistantError.AssistantDisabled)
            if (!settings.privacyNoticeAccepted) throw AgentFailure(AssistantError.PrivacyNoticeNotAccepted)
        }

        /** One model call; `null` = function calls were executed, continue. */
        private suspend fun step(
            context: OpenAiRunContext,
            listener: AgentListener,
            stepsDone: Int,
        ): AgentOutcome? {
            val snapshot = locked { history.toList() }
            val params =
                OpenAiRequestFactory.params(context.model, OpenAiRequestFactory.SYSTEM_PROMPT, context.tools, snapshot)
            val response = gateway.create(context.apiKey, params)
            val items = OpenAiResponseReader.inputItems(response)
            locked { history.addAll(items) }
            val calls = OpenAiResponseReader.functionCalls(response)
            return when {
                calls.isEmpty() -> {
                    // A prose-only response must not silently replace the highlight workflow.
                    if (stepsDone >= context.stepLimit) {
                        AgentOutcome.StepLimitReached(context.stepLimit)
                    } else {
                        locked {
                            history.add(
                                OpenAiRequestFactory.userMessage(
                                    AgentInput.Text(
                                        "Use a highlight tool for the next phone action, " +
                                            "or reply_to_user for a question/completion/limitation. " +
                                            "Do not replace highlights with written instructions.",
                                    ),
                                ),
                            )
                        }
                        null
                    }
                }

                calls.size == 1 && calls.single().name() == GuidanceReply.NAME -> {
                    val call = calls.single()
                    val message = GuidanceReply.message(call)
                    locked {
                        history.add(
                            if (message == null) {
                                OpenAiToolOutput.invalidArguments(call.callId())
                            } else {
                                OpenAiToolOutput.fromResult(
                                    call.callId(),
                                    com.danielealbano.androidremotecontrolmcp.mcp.tools.McpToolUtils
                                        .textResult("Shown to user."),
                                )
                            },
                        )
                    }
                    if (message != null) {
                        listener.onAssistantText(message)
                        AgentOutcome.Completed
                    } else if (stepsDone >= context.stepLimit) {
                        AgentOutcome.StepLimitReached(context.stepLimit)
                    } else {
                        null
                    }
                }

                stepsDone >= context.stepLimit -> {
                    AgentOutcome.StepLimitReached(context.stepLimit)
                }

                else -> {
                    val outputs = calls.map { toolRunner.execute(it, listener, context) }
                    locked { history.addAll(outputs) }
                    null
                }
            }
        }

        private fun truncate(mark: Int) {
            if (history.size > mark) history.subList(mark, history.size).clear()
        }

        private inline fun <T> locked(block: () -> T): T = synchronized(lock, block)
    }
