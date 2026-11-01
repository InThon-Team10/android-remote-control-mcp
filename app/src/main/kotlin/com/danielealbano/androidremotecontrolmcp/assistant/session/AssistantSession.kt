package com.danielealbano.androidremotecontrolmcp.assistant.session

import android.util.Log
import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentInput
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentListener
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AgentOutcome
import com.danielealbano.androidremotecontrolmcp.assistant.agent.AssistantAgent
import com.danielealbano.androidremotecontrolmcp.data.model.AssistantConfig
import com.danielealbano.androidremotecontrolmcp.data.repository.AssistantSettings
import com.danielealbano.androidremotecontrolmcp.di.DefaultDispatcher
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Application-scoped owner of the assistant conversation. Runs and agent resets are serialised by [runMutex];
 * the callbacks and outcome of a stopped run are ignored.
 */
@Singleton
class AssistantSession
    @Inject
    constructor(
        private val agent: AssistantAgent,
        private val settings: AssistantSettings,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val runMutex = Mutex()
        private val lock = Any()
        internal val transcript = ChatTranscript()
        private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
        private val _collapseRequests =
            MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private val pendingConfirmations = HashMap<String, CompletableDeferred<Boolean>>()
        private var runJob: Job? = null

        val items: StateFlow<List<ChatItem>> = transcript.items
        val state: StateFlow<SessionState> = _state.asStateFlow()
        val attention: StateFlow<Boolean> = transcript.attention
        val chatVisible: StateFlow<Boolean> = transcript.chatVisible

        /** Emits when the agent needs the chat window hidden; must be collected while the chat is visible. */
        val collapseRequests: SharedFlow<Unit> = _collapseRequests.asSharedFlow()

        init {
            scope.launch {
                settings.config
                    .map { it.enabled && it.apiKeyConfigured }
                    .distinctUntilChanged()
                    .collect { usable -> if (!usable) stop() }
            }
        }

        /** Set by the UI: true while the chat window is visible. */
        fun setChatVisible(visible: Boolean) = transcript.setChatVisible(visible)

        /** Ignored when not [SessionState.Idle] or [text] is blank. */
        fun send(text: String) {
            Log.d("MCP:VoiceTrace", "session send len=${text.length} state=${_state.value.javaClass.simpleName}")
            if (text.isBlank()) return
            startRun(AgentInput.Text(text)) { id -> ChatItem.User(id, text) }
        }

        /**
         * Marks [job] (a live voice conversation) as the active run and returns its tool-call listener, or null
         * when not [SessionState.Idle]. The session returns to Idle when [job] completes; [stop]/[newChat] cancel it.
         */
        fun beginLive(job: Job): AgentListener? =
            synchronized(lock) {
                if (_state.value != SessionState.Idle) return null
                runJob = job
                _state.value = SessionState.Running
                job.invokeOnCompletion {
                    synchronized(lock) {
                        if (runJob === job) {
                            runJob = null
                            _state.value = SessionState.Idle
                        }
                    }
                }
                RunListener(job)
            }

        /** Unknown or already answered [callId] is ignored. */
        fun answerConfirmation(
            callId: String,
            allow: Boolean,
        ) {
            val deferred =
                synchronized(lock) {
                    pendingConfirmations.remove(callId)?.also {
                        transcript.update { item ->
                            if (item is ChatItem.Confirmation && item.callId == callId) {
                                item.copy(answered = allow)
                            } else {
                                item
                            }
                        }
                        _state.value =
                            if (pendingConfirmations.isEmpty()) {
                                SessionState.Running
                            } else {
                                SessionState.AwaitingConfirmation
                            }
                    }
                } ?: return
            deferred.complete(allow)
        }

        /** Cancels the active run (no-op when idle); the user's own stop raises no attention. */
        fun stop() {
            Log.d("MCP:VoiceTrace", "session stop state=${_state.value.javaClass.simpleName}")
            synchronized(lock) {
                if (halt()) transcript.append { id -> ChatItem.Notice(id, NoticeKind.Stopped) }
            }
        }

        /** Appends [error] to the transcript without touching the run state. */
        fun reportError(error: AssistantError) = transcript.append { id -> ChatItem.Error(id, error) }

        /** Stop (no notice) + clear the transcript + reset the agent once the cancelled run has unwound. */
        fun newChat() {
            synchronized(lock) {
                halt()
                transcript.clear()
                scope.launch { runMutex.withLock { agent.reset() } }
            }
        }

        private fun startRun(
            input: AgentInput,
            userItem: (Long) -> ChatItem,
        ) {
            synchronized(lock) {
                if (_state.value != SessionState.Idle) return
                transcript.append(userItem)
                _state.value = SessionState.Running
                runJob = scope.launch { runMutex.withLock { execute(input) } }
            }
        }

        private suspend fun execute(input: AgentInput) {
            val job = currentCoroutineContext().job
            val outcome =
                runCatching {
                    gateError(settings.getConfig())?.let { AgentOutcome.Failed(it) }
                        ?: agent.run(input, RunListener(job))
                }.getOrElse { error ->
                    if (error is Error) throw error
                    if (error is CancellationException) currentCoroutineContext().ensureActive()
                    Logger.e(TAG, "Assistant run failed: ${error.javaClass.simpleName}")
                    AgentOutcome.Failed(AssistantError.ModelRequestFailed(error.javaClass.simpleName))
                }
            synchronized(lock) {
                if (!job.isActive) return
                when (outcome) {
                    AgentOutcome.Completed -> {}

                    is AgentOutcome.StepLimitReached -> {
                        transcript.append { id -> ChatItem.Notice(id, NoticeKind.StepLimitReached(outcome.limit)) }
                    }

                    is AgentOutcome.Failed -> {
                        transcript.append { id -> ChatItem.Error(id, outcome.error) }
                    }
                }
                runJob = null
                _state.value = SessionState.Idle
            }
        }

        /** Caller holds [lock]. Cancels the active run; returns false when none was active. */
        private fun halt(): Boolean {
            val job = runJob ?: return false
            runJob = null
            job.cancel()
            pendingConfirmations.clear()
            transcript.update { item ->
                when {
                    item is ChatItem.ToolCall && item.status == ToolCallStatus.RUNNING -> {
                        item.copy(status = ToolCallStatus.STOPPED)
                    }

                    item is ChatItem.Confirmation && item.answered == null -> {
                        item.copy(answered = false)
                    }

                    else -> {
                        item
                    }
                }
            }
            _state.value = SessionState.Idle
            return true
        }

        private inner class RunListener(
            private val job: Job,
        ) : AgentListener {
            override fun onAssistantText(text: String) {
                ifActive { transcript.append { id -> ChatItem.Assistant(id, text) } }
            }

            override suspend fun beforeToolCall() {
                if (!transcript.chatVisible.value) return
                _collapseRequests.tryEmit(Unit)
                withTimeoutOrNull(COLLAPSE_TIMEOUT_MS) { transcript.chatVisible.first { visible -> !visible } }
            }

            override fun onToolCallStarted(
                callId: String,
                toolName: String,
            ) = ifActive {
                transcript.append { id -> ChatItem.ToolCall(id, callId, toolName, ToolCallStatus.RUNNING) }
            }

            override fun onToolCallFinished(
                callId: String,
                toolName: String,
                isError: Boolean,
            ) = ifActive {
                transcript.update { item ->
                    if (item is ChatItem.ToolCall && item.callId == callId && item.status == ToolCallStatus.RUNNING) {
                        item.copy(status = if (isError) ToolCallStatus.FAILED else ToolCallStatus.DONE)
                    } else {
                        item
                    }
                }
            }

            override suspend fun confirm(
                callId: String,
                toolName: String,
            ): Boolean {
                val deferred = CompletableDeferred<Boolean>()
                synchronized(lock) {
                    if (!job.isActive) throw CancellationException("Assistant run is no longer active")
                    pendingConfirmations[callId] = deferred
                    transcript.append { id -> ChatItem.Confirmation(id, callId, toolName, null) }
                    _state.value = SessionState.AwaitingConfirmation
                }
                val allow =
                    try {
                        deferred.await()
                    } finally {
                        synchronized(lock) { pendingConfirmations.remove(callId) }
                    }
                if (!allow) {
                    ifActive {
                        transcript.append { id -> ChatItem.ToolCall(id, callId, toolName, ToolCallStatus.DENIED) }
                    }
                }
                return allow
            }

            private inline fun ifActive(block: () -> Unit) {
                synchronized(lock) { if (job.isActive) block() }
            }
        }

        private companion object {
            const val TAG = "MCP:AssistantSession"
            const val COLLAPSE_TIMEOUT_MS = 2_000L
        }
    }

private fun gateError(config: AssistantConfig): AssistantError? =
    when {
        !config.enabled -> AssistantError.AssistantDisabled
        !config.privacyNoticeAccepted -> AssistantError.PrivacyNoticeNotAccepted
        !config.apiKeyConfigured -> AssistantError.ApiKeyMissing
        else -> null
    }
