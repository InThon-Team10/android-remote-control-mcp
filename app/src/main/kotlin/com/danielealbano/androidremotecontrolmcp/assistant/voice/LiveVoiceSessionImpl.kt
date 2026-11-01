package com.danielealbano.androidremotecontrolmcp.assistant.voice

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantException
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.OpenAiErrorMapper
import com.danielealbano.androidremotecontrolmcp.assistant.guide.GuideSteps
import com.danielealbano.androidremotecontrolmcp.assistant.session.AssistantSession
import com.danielealbano.androidremotecontrolmcp.assistant.session.appendLiveAssistantText
import com.danielealbano.androidremotecontrolmcp.assistant.session.appendLiveUserText
import com.danielealbano.androidremotecontrolmcp.assistant.session.endLiveTurn
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.di.IoDispatcher
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Live voice conversation over the OpenAI Realtime API. One [Job] per conversation: cancelling it (stop, session
 * stop/newChat, assistant disabled, error) ends the capture, the message loop and the tool calls, which run in a scope
 * tied to it, and releases everything.
 */
@Singleton
class LiveVoiceSessionImpl
    @Inject
    constructor(
        private val gateway: RealtimeGateway,
        private val capture: AudioCapture,
        private val playback: AudioPlayback,
        private val launcher: VoiceServiceLauncher,
        private val deps: LiveVoiceDependencies,
    ) : LiveVoiceSession {
        private val startMutex = Mutex()
        private val _state = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Idle)
        private var current: Job? = null

        @Volatile private var handler: RealtimeEventHandler? = null

        override val state: StateFlow<LiveVoiceState> = _state.asStateFlow()

        override suspend fun start(): Result<Unit> =
            startMutex.withLock {
                if (current?.isActive == true) return@withLock Result.success(Unit)
                val job = Job()
                runCatching { connect(job) }
                    .onSuccess { current = job }
                    .fold({ Result.success(Unit) }) { error -> failStart(job, error) }
            }

        private fun failStart(
            job: Job,
            error: Throwable,
        ): Result<Unit> {
            job.cancel()
            if (error is CancellationException || error is Error) throw error
            val mapped = (error as? AssistantException)?.error ?: OpenAiErrorMapper.map(error)
            _state.value = LiveVoiceState.Error(mapped)
            Logger.w(TAG, "Live voice start failed: ${error.javaClass.simpleName}")
            return Result.failure(AssistantException(mapped))
        }

        override fun sendText(text: String) {
            text.takeIf { it.isNotBlank() }?.let { handler?.sendText(it) }
        }

        override suspend fun stop() {
            Log.d(VOICE_TRACE, "live stop requested (active=${current?.isActive == true})")
            current?.cancel()
            if (_state.value is LiveVoiceState.Error) _state.value = LiveVoiceState.Idle
        }

        private suspend fun connect(job: Job) {
            _state.value = LiveVoiceState.Connecting
            val config = deps.settings.getConfig()
            val apiKey = deps.settings.getApiKey()
            preflightError(config, apiKey)?.let { throw AssistantException(it) }
            checkNotNull(apiKey)
            val listener =
                deps.session.beginLive(job)
                    ?: throw AssistantException(AssistantError.ModelRequestFailed("Assistant is busy"))
            val scope = CoroutineScope(SupervisorJob() + deps.dispatcher)
            job.invokeOnCompletion { scope.cancel() }
            val inbound = Channel<JsonObject>(Channel.UNLIMITED)
            job.invokeOnCompletion { release(inbound) }
            val heard = AtomicBoolean(true)
            val link =
                gateway.connect(
                    apiKey,
                    { heard.set(true) }.let { mark ->
                        { e: JsonObject ->
                            mark()
                            inbound.trySend(e)
                        }
                    },
                ) {
                    onClosed(job, it)
                }
            job.invokeOnCompletion { link.close() }
            val events = Events(job)
            val queue = RealtimeSendQueue(scope, link, SEND_TIMEOUT_MS) { onSendFailed(events, it) }
            queue.enqueue(RealtimeProtocol.sessionUpdate())
            val eventHandler = RealtimeEventHandler(queue::enqueue, playback, deps.guide, listener, scope, events)
            handler = eventHandler
            eventHandler.greet()
            playback.start()
            launcher.start()
            scope.guarded(events) { for (event in inbound) eventHandler.handle(event) }
            scope.guarded(events, AssistantError.VoiceRecordingFailed) {
                val gate = EchoGate(playback::pendingMs, { System.nanoTime() / NANOS_PER_MS })
                capture.chunks().collect { chunk ->
                    if (gate.allows()) queue.enqueue(RealtimeProtocol.audioAppend(chunk))
                }
            }
            scope.launch { LinkWatchdog.run(heard, queue::enqueue, events) }
            scope.launch {
                delay(SESSION_LIMIT_MS)
                events.end(AgentOutcome.Completed)
            }
            _state.value = LiveVoiceState.Listening
            Log.d(VOICE_TRACE, "live connected")
        }

        private fun onSendFailed(
            events: LiveEvents,
            error: Throwable,
        ) {
            Logger.w(TAG, "Live voice send failed: ${error.javaClass.simpleName}")
            events.end(AgentOutcome.Failed(AssistantError.ModelUnavailable))
        }

        private fun preflightError(
            config: AssistantConfig,
            apiKey: String?,
        ): AssistantError? =
            gateError(config)
                ?: AssistantError.MicrophonePermissionMissing.takeUnless { capture.hasPermission() }
                ?: AssistantError.ApiKeyMissing.takeIf { apiKey == null }

        private fun CoroutineScope.guarded(
            events: LiveEvents,
            fallback: AssistantError? = null,
            block: suspend () -> Unit,
        ) = launch {
            val failure = runCatching { block() }.exceptionOrNull() ?: return@launch
            if (failure is CancellationException || failure is Error) throw failure
            if (failure is SecurityException) {
                events.end(AgentOutcome.Failed(AssistantError.MicrophonePermissionMissing))
            } else {
                Logger.w(TAG, "Live voice failed: ${failure.javaClass.simpleName}")
                events.end(AgentOutcome.Failed(fallback ?: OpenAiErrorMapper.map(failure)))
            }
        }

        private fun release(messages: Channel<*>) {
            handler = null
            messages.close()
            deps.guide.reset()
            playback.release()
            launcher.stop()
            if (_state.value !is LiveVoiceState.Error) _state.value = LiveVoiceState.Idle
        }

        private fun onClosed(
            job: Job,
            error: Throwable?,
        ) {
            if (!job.isActive) return
            Log.d(VOICE_TRACE, "live socket closed: ${error?.javaClass?.simpleName ?: "normally"}")
            val outcome =
                if (error == null) AgentOutcome.Completed else AgentOutcome.Failed(OpenAiErrorMapper.map(error))
            Events(job).end(outcome)
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
                if (job.isActive && _state.value != LiveVoiceState.Speaking) {
                    Log.d(VOICE_TRACE, "live state -> Speaking")
                    _state.value = LiveVoiceState.Speaking
                }
            }

            override fun listening() {
                if (job.isActive) {
                    Log.d(VOICE_TRACE, "live state -> Listening")
                    _state.value = LiveVoiceState.Listening
                }
            }

            override fun end(outcome: AgentOutcome) {
                if (!job.isActive) return
                Log.d(VOICE_TRACE, "live end: ${outcome.javaClass.simpleName}")
                when (outcome) {
                    AgentOutcome.Completed, is AgentOutcome.StepLimitReached -> {
                        Unit
                    }

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

            /** A send stuck this long means the link is dead (the connection has no ping). */
            const val SEND_TIMEOUT_MS = 15_000L
            const val NANOS_PER_MS = 1_000_000L

            /** Realtime sessions last 60 minutes at most: end gracefully just before. */
            const val SESSION_LIMIT_MS = 59 * 60_000L
        }
    }

/** Collaborators of [LiveVoiceSessionImpl] bundled to keep its constructor small. */
class LiveVoiceDependencies
    @Inject
    constructor(
        val settings: AssistantSettings,
        val session: AssistantSession,
        val guide: GuideSteps,
        @IoDispatcher val dispatcher: CoroutineDispatcher,
    )
