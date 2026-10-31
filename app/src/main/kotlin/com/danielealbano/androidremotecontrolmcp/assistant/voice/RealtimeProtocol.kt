package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.ElderGuidancePrompt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/** Client events of the Realtime API (wire format) and the two tools of the voice model. */
internal object RealtimeProtocol {
    const val SHOW_NEXT_STEP = "show_next_step"
    const val WAIT_FOR_USER = "wait_for_user"
    private const val TRANSCRIPTION_MODEL = "gpt-transcribe"
    private const val LANGUAGE = "ko"
    private const val VOICE = "marin"
    private const val EFFORT = "low"

    fun sessionUpdate(): JsonObject =
        buildJsonObject {
            put("type", "session.update")
            putJsonObject("session") {
                put("type", "realtime")
                put("instructions", ElderGuidancePrompt.VOICE)
                putJsonObject("audio") {
                    putJsonObject("input") {
                        putJsonObject("format") { pcm() }
                        putJsonObject("transcription") {
                            put("model", TRANSCRIPTION_MODEL)
                            put("language", LANGUAGE)
                        }
                        putJsonObject("turn_detection") {
                            put("type", "semantic_vad")
                            put("eagerness", "low")
                            put("create_response", true)
                            // The microphone is muted while the assistant speaks (EchoGate); a leak of its own
                            // voice must never cancel the reply mid-sentence.
                            put("interrupt_response", false)
                        }
                    }
                    putJsonObject("output") {
                        putJsonObject("format") { pcm() }
                        put("voice", VOICE)
                    }
                }
                put("tools", tools())
                put("tool_choice", "auto")
                putJsonObject("reasoning") { put("effort", EFFORT) }
            }
        }

    private fun kotlinx.serialization.json.JsonObjectBuilder.pcm() {
        put("type", "audio/pcm")
        put("rate", LiveAudio.SAMPLE_RATE)
    }

    private fun tools(): JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "function")
                    put("name", SHOW_NEXT_STEP)
                    put(
                        "description",
                        "Finds the one thing the user must touch next towards the goal and highlights it on the " +
                            "screen. Result: {say, showing, kind}. Say the sentence in 'say' exactly.",
                    )
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("goal") {
                                put("type", "string")
                                put("description", "What the user wants to do, in Korean.")
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("goal")) }
                    }
                },
            )
            add(
                buildJsonObject {
                    put("type", "function")
                    put("name", WAIT_FOR_USER)
                    put(
                        "description",
                        "Waits until the user did the highlighted step, the screen changed, the highlight timed " +
                            "out or was closed. Call it right after saying a step that is showing.",
                    )
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {}
                    }
                },
            )
        }

    fun audioAppend(pcm: ByteArray): JsonObject =
        buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", Base64.getEncoder().encodeToString(pcm))
        }

    fun functionOutput(
        callId: String,
        output: String,
    ): JsonObject =
        buildJsonObject {
            put("type", "conversation.item.create")
            putJsonObject("item") {
                put("type", "function_call_output")
                put("call_id", callId)
                put("output", output)
            }
        }

    fun userText(text: String): JsonObject =
        buildJsonObject {
            put("type", "conversation.item.create")
            putJsonObject("item") {
                put("type", "message")
                put("role", "user")
                putJsonArray("content") {
                    add(
                        buildJsonObject {
                            put("type", "input_text")
                            put("text", text)
                        },
                    )
                }
            }
        }

    fun responseCreate(): JsonObject = buildJsonObject { put("type", "response.create") }

    /** An app note in the conversation (step progress the model reads out). */
    fun systemNote(text: String): JsonObject =
        buildJsonObject {
            put("type", "conversation.item.create")
            putJsonObject("item") {
                put("type", "message")
                put("role", "system")
                putJsonArray("content") {
                    add(
                        buildJsonObject {
                            put("type", "input_text")
                            put("text", text)
                        },
                    )
                }
            }
        }

    /** The first reply of a conversation: one short greeting, no tools. */
    fun greeting(): JsonObject =
        buildJsonObject {
            put("type", "response.create")
            putJsonObject("response") {
                put("instructions", GREETING)
                put("tool_choice", "none")
            }
        }

    fun decodeAudio(base64: String): ByteArray = Base64.getDecoder().decode(base64)

    private const val GREETING =
        "대화를 막 시작했어요. 한국어 존댓말로 한 문장만 짧게 인사하고 무엇을 도와드릴지 물어보세요. " +
            "예: '안녕하세요, 무엇을 도와드릴까요?'"
}
