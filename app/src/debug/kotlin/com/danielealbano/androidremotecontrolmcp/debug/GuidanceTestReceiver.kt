package com.danielealbano.androidremotecontrolmcp.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.danielealbano.androidremotecontrolmcp.services.guidance.DeepSeekGuidanceAgent
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceController
import com.danielealbano.androidremotecontrolmcp.services.guidance.GuidanceSession
import com.danielealbano.androidremotecontrolmcp.services.guidance.HighlightBounds
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Shell-only smoke-test adapter for the same API a future UI calls. Absent from release builds. */
@AndroidEntryPoint
class GuidanceTestReceiver : BroadcastReceiver() {
    @Inject lateinit var agent: DeepSeekGuidanceAgent

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val state =
                    when (intent.getStringExtra("operation") ?: "start") {
                        "agent_start" -> {
                            agent.start(
                                requireNotNull(intent.getStringExtra("api_key")) { "api_key is required" },
                                intent.getStringExtra("goal") ?: GuidanceSession.TEST_GOAL,
                            )
                        }

                        "agent_stop" -> {
                            agent.stop()
                            GuidanceController.state.value
                        }

                        "start" -> {
                            GuidanceController.start(intent.getStringExtra("goal") ?: GuidanceSession.TEST_GOAL)
                        }

                        "show" -> {
                            GuidanceController.show(
                                intent.getLongExtra("revision", -1),
                                intent.getStringExtra("instruction") ?: "표시된 부분을 누르세요.",
                                HighlightBounds(
                                    intent.getIntExtra("left", -1),
                                    intent.getIntExtra("top", -1),
                                    intent.getIntExtra("right", -1),
                                    intent.getIntExtra("bottom", -1),
                                ),
                            )
                        }

                        "status" -> {
                            GuidanceController.state.value
                        }

                        "complete" -> {
                            GuidanceController.complete(intent.getLongExtra("revision", -1))
                        }

                        "cancel" -> {
                            GuidanceController.cancel()
                        }

                        else -> {
                            error("Unknown guidance operation")
                        }
                    }
                pending.resultCode = 0
                pending.resultData = Json.encodeToString(state)
            } catch (error: IllegalArgumentException) {
                pending.resultCode = 1
                pending.resultData = error.message
            } catch (error: IllegalStateException) {
                pending.resultCode = 1
                pending.resultData = error.message
            } finally {
                pending.finish()
            }
        }
    }
}
