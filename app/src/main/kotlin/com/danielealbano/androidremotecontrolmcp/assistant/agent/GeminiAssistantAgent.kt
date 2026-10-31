package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.google.genai.types.Content
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Gemini implementation of [AssistantAgent]: our own function-calling loop over the app's MCP tools. */
@Singleton
class GeminiAssistantAgent
    @Inject
    constructor(
        private val gateway: GeminiModelGateway,
        private val connection: AssistantMcpConnection,
        private val assistantSettings: AssistantSettings,
        private val settingsRepository: SettingsRepository,
        private val toolRunner: GeminiToolCallRunner,
    ) : AssistantAgent {
        private val runMutex = Mutex()
        private val lock = Any()
        private val history = mutableListOf<Content>()

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
                        AgentOutcome.Failed(GeminiErrorMapper.map(error))
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
            val tools = connection.listTools().getOrElse { throw it.toMcpFailure() }
            val context =
                RunContext(
                    apiKey = apiKey,
                    model = settings.model,
                    config = GeminiRequestFactory.config(tools),
                    stepLimit = settings.stepLimit,
                    deviceSlug = settingsRepository.getServerConfig().deviceSlug,
                    toolNames = tools.mapTo(HashSet()) { it.name },
                )
            locked { history.add(GeminiRequestFactory.userContent(input)) }
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
            context: RunContext,
            listener: AgentListener,
            stepsDone: Int,
        ): AgentOutcome? {
            val snapshot = locked { history.toList() }
            val response = gateway.generate(context.apiKey, context.model, snapshot, context.config)
            val content = GeminiResponseReader.modelContent(response)
            locked { history.add(content) }
            GeminiResponseReader.texts(content).forEach(listener::onAssistantText)
            val calls = GeminiResponseReader.functionCalls(content)
            return when {
                calls.isEmpty() -> {
                    AgentOutcome.Completed
                }

                stepsDone >= context.stepLimit -> {
                    AgentOutcome.StepLimitReached(context.stepLimit)
                }

                else -> {
                    val responses = calls.map { toolRunner.execute(it, listener, context) }
                    val turn =
                        Content
                            .builder()
                            .role(GeminiRequestFactory.USER_ROLE)
                            .parts(responses)
                            .build()
                    locked { history.add(turn) }
                    null
                }
            }
        }

        private fun truncate(mark: Int) {
            if (history.size > mark) history.subList(mark, history.size).clear()
        }

        private inline fun <T> locked(block: () -> T): T = synchronized(lock, block)
    }
