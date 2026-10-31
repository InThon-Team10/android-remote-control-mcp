package com.danielealbano.androidremotecontrolmcp.services.guidance

import com.danielealbano.androidremotecontrolmcp.mcp.tools.GetScreenStateHandler
import com.danielealbano.androidremotecontrolmcp.mcp.tools.GuidanceTools
import com.danielealbano.androidremotecontrolmcp.services.accessibility.McpAccessibilityService
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** API key lives only in the running coroutine, never in app preferences or logs. */
@Singleton
class DeepSeekGuidanceAgent
    @Inject
    constructor(
        private val screen: GetScreenStateHandler,
        private val planner: DeepSeekGuidancePlanner,
        private val guidance: GuidanceTools,
    ) {
        private var job: Job? = null
        private val mutableStatus = MutableStateFlow("IDLE")
        val status = mutableStatus.asStateFlow()

        suspend fun start(
            apiKey: String,
            goal: String = GuidanceSession.TEST_GOAL,
        ): GuidanceState =
            withContext(Dispatchers.Main.immediate) {
                require(apiKey.isNotBlank()) { "DeepSeek API key is required" }
                stop()
                val started = GuidanceController.start(goal)
                val scope = checkNotNull(McpAccessibilityService.instance?.getServiceScope())
                job =
                    scope.launch {
                        GuidanceController.state
                            .takeWhile { state ->
                                val active =
                                    state.phase in
                                        setOf(
                                            GuidancePhase.NEEDS_ANALYSIS,
                                            GuidancePhase.WAITING_USER,
                                        )
                                if (!active) mutableStatus.value = state.phase.name
                                active
                            }.collectLatest { state ->
                                if (state.phase == GuidancePhase.NEEDS_ANALYSIS) {
                                    delay(SETTLE_MS)
                                    analyze(apiKey, state)
                                } else {
                                    mutableStatus.value = state.phase.name
                                }
                            }
                    }
                started
            }

        suspend fun stop() =
            withContext(Dispatchers.Main.immediate) {
                job?.cancelAndJoin()
                job = null
                mutableStatus.value = "IDLE"
                GuidanceController.cancel()
            }

        private suspend fun analyze(
            apiKey: String,
            state: GuidanceState,
        ) {
            mutableStatus.value = "ANALYZING"
            try {
                val snapshot =
                    screen.execute(
                        buildJsonObject {
                            put("include_screenshot", false)
                            put("include_offscreen", false)
                            put("include_hierarchy", true)
                        },
                    )
                check(snapshot.isError != true) { "Screen capture unavailable" }
                val text = snapshot.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
                val decision = planner.plan(apiKey, state, text)
                if (GuidanceController.state.value.revision != state.revision) return
                when (decision.status) {
                    "guide" -> {
                        val result =
                            guidance.execute(
                                "show",
                                buildJsonObject {
                                    put("revision", state.revision)
                                    put("instruction", decision.instruction)
                                    decision.nodeId?.let { put("node_id", it) }
                                    decision.bounds?.let {
                                        put("left", it.left)
                                        put("top", it.top)
                                        put("right", it.right)
                                        put("bottom", it.bottom)
                                    }
                                },
                            )
                        check(result.isError != true) { "Model selected an unavailable target" }
                    }

                    "complete" -> {
                        GuidanceController.complete(state.revision)
                    }

                    "blocked" -> {
                        GuidanceController.fail(decision.instruction)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (
                @Suppress("TooGenericExceptionCaught") error: Exception,
            ) {
                mutableStatus.value = "ERROR"
                val diagnostic = error.javaClass.simpleName
                GuidanceController.fail("AI 안내를 가져오지 못했습니다. 설정을 확인하고 다시 시작하세요. ($diagnostic)")
            }
        }

        private companion object {
            const val SETTLE_MS = 600L
        }
    }
