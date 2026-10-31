package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiErrorMapper
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiRequestFactory
import com.danielealbano.androidremotecontrolmcp.assistant.agent.GeminiToolCallRunner
import com.danielealbano.androidremotecontrolmcp.assistant.agent.McpToolSchemaMapper
import com.danielealbano.androidremotecontrolmcp.assistant.agent.RunContext
import com.danielealbano.androidremotecontrolmcp.assistant.agent.toMcpFailure
import com.danielealbano.androidremotecontrolmcp.assistant.mcp.AssistantMcpConnection
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.appendLiveAssistantText
import com.danielealbano.androidremotecontrolmcp.assistant.session.appendLiveUserText
import com.danielealbano.androidremotecontrolmcp.assistant.session.endLiveTurn
import com.danielealbano.androidremotecontrolmcp.assistant.session.reportStepLimit
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.data.repository.SettingsRepository
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import com.google.genai.types.AudioTranscriptionConfig
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.LiveConnectConfig
import com.google.genai.types.LiveServerMessage
import com.google.genai.types.Modality
import com.google.genai.types.Part
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Live voice conversation over the Gemini Live API. One [Job] per conversation (cancelling it ends the capture, the
 * message loop and the tool calls, which run in a scope tied to it) (stop, session stop/newChat, assistant disabled, error) releases everything.
 */
@Singleton
class LiveVoiceSessionImpl
    @Inject
    constructor(
        private val gateway: LiveGateway,
        private val capture: AudioCapture,
        private val playback: AudioPlayback,
        private val launcher: VoiceServiceLauncher,
        private val deps: LiveVoiceDependencies,
    ) : LiveVoiceSession {
        private val startMutex = Mutex()
        private val _state = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
        private var current: Job? = null

        override val state: StateFlow<LiveVoiceState> = _state.asStateFlow()

        override suspend fun start(): Result<Unit> =
            startMutex.withLock {
                if (current?.isActive == true) return@withLock Result.success(Unit)
                val job = Job()
                try {
                    connect(job)
                    current = job
                    Result.success(Unit)
                } catch (error: CancellationException) {
                    job.cancel()
                    throw error
                } catch (error: Exception) {
                    job.cancel()
                    val mapped = (error as? AssistantException)?.error ?: GeminiErrorMapper.map(error)
                    _state.value = LiveVoiceState.Error(mapped)
                    Logger.w(TAG, "Live voice start failed: ${error.javaClass.simpleName}")
                    Result.failure(AssistantException(mapped))
                }
            }

        override suspend fun stop() {
            current?.cancel()
            if (_state.value is LiveVoiceState.Error) _state.value = LiveVoiceState.Idle
        }

        private suspend fun connect(job: Job) {
            _state.value = LiveVoiceState.Connecting
            val config = deps.settings.getConfig()
            val apiKey = deps.settings.getApiKey()
            gateError(config)?.let { throw AssistantException(it) }
            if (!capture.hasPermission()) throw AssistantException(AssistantError.MicrophonePermissionMissing)
            if (apiKey == null) throw AssistantException(AssistantError.ApiKeyMissing)
            val listener =
                deps.session.beginLive(job)
                    ?: throw AssistantException(AssistantError.GeminiRequestFailed("Assistant is busy"))
            val tools = deps.connection.listTools().getOrElse { throw it.toMcpFailure() }
            val scope = CoroutineScope(SupervisorJob() + deps.dispatcher)
            job.invokeOnCompletion { scope.cancel() }
            val messages = Channel<LiveServerMessage>(Channel.UNLIMITED)
            job.invokeOnCompletion { release(messages) }
            val link = gateway.connect(apiKey, liveConfig(tools), { messages.trySend(it) }) { onClosed(job, it) }
            job.invokeOnCompletion { link.close() }
            val context =
                RunContext(
                    apiKey = apiKey,
                    model = AssistantConfig.LIVE_MODEL,
                    config = GenerateContentConfig.builder().build(),
                    stepLimit = config.stepLimit,
                    deviceSlug = deps.settingsRepository.getServerConfig().deviceSlug,
                    toolNames = tools.mapTo(HashSet()) { it.name },
                )
            val events = Events(job)
            val handler =
                LiveMessageHandler(link, playback, LiveToolRunner(deps.runner, listener, context), scope, events)
            playback.start()
            launcher.start()
            scope.guarded(events) { for (message in messages) handler.handle(message) }
            scope.guarded(events, AssistantError.VoiceRecordingFailed) {
                capture.chunks().collect { link.sendAudio(it) }
            }
            _state.value = LiveVoiceState.Listening
        }

        private fun CoroutineScope.guarded(
            events: LiveEvents,
            fallback: AssistantError? = null,
            block: suspend () -> Unit,
        ) = launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: SecurityException) {
                events.end(AgentOutcome.Failed(AssistantError.MicrophonePermissionMissing))
            } catch (error: Exception) {
                Logger.w(TAG, "Live voice failed: ${error.javaClass.simpleName}")
                events.end(AgentOutcome.Failed(fallback ?: GeminiErrorMapper.map(error)))
            }
        }

        private fun release(messages: Channel<*>) {
            messages.close()
            playback.release()
            launcher.stop()
            if (_state.value !is LiveVoiceState.Error) _state.value = LiveVoiceState.Idle
        }

        private fun onClosed(
            job: Job,
            error: Throwable?,
        ) {
            if (!job.isActive) return
            val outcome =
                if (error == null) AgentOutcome.Completed else AgentOutcome.Failed(GeminiErrorMapper.map(error))
            Events(job).end(outcome)
        }

        private fun liveConfig(tools: List<Tool>): LiveConnectConfig {
            val builder =
                LiveConnectConfig
                    .builder()
                    .responseModalities(Modality(Modality.Known.AUDIO))
                    .inputAudioTranscription(AudioTranscriptionConfig.builder())
                    .outputAudioTranscription(AudioTranscriptionConfig.builder())
                    .systemInstruction(Content.fromParts(Part.fromText(GeminiRequestFactory.SYSTEM_PROMPT)))
            if (tools.isNotEmpty()) builder.tools(listOf(McpToolSchemaMapper.toGeminiTool(tools)))
            return builder.build()
        }

        private fun gateError(config: AssistantConfig): AssistantError? =
            when {
                !config.enabled -> AssistantError.AssistantDisabled
                !config.privacyNoticeAccepted -> AssistantError.PrivacyNoticeNotAccepted
                !config.voiceEnabled -> AssistantError.VoiceDisabled
                else -> null
            }

        private inner class Events(
            private val job: Job,
        ) : LiveEvents {
            override fun userText(text: String) = deps.session.appendLiveUserText(text)

            override fun assistantText(text: String) = deps.session.appendLiveAssistantText(text)

            override fun turnEnd() = deps.session.endLiveTurn()

            override fun speaking() {
                if (job.isActive) _state.value = LiveVoiceState.Speaking
            }

            override fun listening() {
                if (job.isActive) _state.value = LiveVoiceState.Listening
            }

            override fun end(outcome: AgentOutcome) {
                if (!job.isActive) return
                when (outcome) {
                    AgentOutcome.Completed -> Unit
                    is AgentOutcome.StepLimitReached -> deps.session.reportStepLimit(outcome.limit)
                    is AgentOutcome.Failed -> {
                        deps.session.reportError(outcome.error)
                        _state.value = LiveVoiceState.Error(outcome.error)
                    }
                }
                job.cancel()
            }
        }

        private companion object {
            const val TAG = "MCP:LiveVoice"
        }
    }

/** Collaborators of [LiveVoiceSessionImpl] bundled to keep its constructor small. */
class LiveVoiceDependencies
    @Inject
    constructor(
        val settings: AssistantSettings,
        val settingsRepository: SettingsRepository,
        val session: AssistantSession,
        val connection: AssistantMcpConnection,
        val runner: GeminiToolCallRunner,
        @IoDispatcher val dispatcher: CoroutineDispatcher,
    )
