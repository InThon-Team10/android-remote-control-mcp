package com.danielealbano.androidremotecontrolmcp.assistant.voice

import com.danielealbano.androidremotecontrolmcp.assistant.agent.ElderGuidancePrompt
import com.openai.core.jsonMapper
import com.openai.models.realtime.RealtimeClientEvent
import com.openai.models.realtime.RealtimeServerEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RealtimeProtocol")
class RealtimeProtocolTest {
    @Test
    fun `audio runs at 24 kHz in 100 ms chunks`() {
        assertEquals(24_000, LiveAudio.SAMPLE_RATE)
        assertEquals(4800, LiveAudio.CHUNK_BYTES)
        assertEquals(48, LiveAudio.BYTES_PER_MS)
    }

    @Test
    fun `turn detection answers the user but never cuts the assistant off`() {
        val turn =
            RealtimeProtocol
                .sessionUpdate()["session"]!!
                .jsonObject["audio"]!!
                .jsonObject["input"]!!
                .jsonObject["turn_detection"]!!
                .jsonObject

        assertEquals("true", turn["create_response"]!!.jsonPrimitive.content)
        assertEquals("false", turn["interrupt_response"]!!.jsonPrimitive.content)
    }

    @Test
    fun `session update configures audio, tools and the voice prompt`() {
        val session = RealtimeProtocol.sessionUpdate()["session"]!!.jsonObject
        val input = session["audio"]!!.jsonObject["input"]!!.jsonObject
        val output = session["audio"]!!.jsonObject["output"]!!.jsonObject

        assertEquals(ElderGuidancePrompt.VOICE, session["instructions"]!!.jsonPrimitive.content)
        assertEquals(24_000, input["format"]!!.jsonObject["rate"]!!.jsonPrimitive.int)
        assertEquals(24_000, output["format"]!!.jsonObject["rate"]!!.jsonPrimitive.int)
        assertEquals("gpt-transcribe", input["transcription"]!!.jsonObject["model"]!!.jsonPrimitive.content)
        assertEquals("semantic_vad", input["turn_detection"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("marin", output["voice"]!!.jsonPrimitive.content)
        assertEquals("low", session["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val tools: JsonArray = session["tools"]!!.jsonArray
        assertEquals(
            listOf("show_next_step", "wait_for_user"),
            tools.map { it.jsonObject["name"]!!.jsonPrimitive.content },
        )
    }

    @Test
    fun `every client event is accepted by the SDK`() {
        val events =
            listOf(
                RealtimeProtocol.sessionUpdate(),
                RealtimeProtocol.audioAppend(ByteArray(4800)),
                RealtimeProtocol.truncate("item_1", 250),
                RealtimeProtocol.functionOutput("call_1", "{}"),
                RealtimeProtocol.userText("안녕"),
                RealtimeProtocol.responseCreate(),
            )

        for (event in events) {
            val typed = jsonMapper().readValue(event.toString(), RealtimeClientEvent::class.java)
            val back = Json.parseToJsonElement(jsonMapper().writeValueAsString(typed)).jsonObject
            assertEquals(event["type"], back["type"])
        }
    }

    @Test
    fun `a server audio delta survives the SDK round trip`() {
        val wire =
            """{"type":"response.output_audio.delta","event_id":"e1","response_id":"r1","item_id":"i1",""" +
                """"output_index":0,"content_index":0,"delta":"AAAA"}"""

        val typed = jsonMapper().readValue(wire, RealtimeServerEvent::class.java)
        val back = Json.parseToJsonElement(jsonMapper().writeValueAsString(typed)).jsonObject

        assertEquals("response.output_audio.delta", back["type"]!!.jsonPrimitive.content)
        assertEquals("AAAA", back["delta"]!!.jsonPrimitive.content)
        assertEquals("i1", back["item_id"]!!.jsonPrimitive.content)
        assertTrue(back.containsKey("response_id"))
    }

    @Test
    fun `audio append carries base64 pcm`() {
        val event = RealtimeProtocol.audioAppend(byteArrayOf(1, 2, 3))

        assertEquals("AQID", event["audio"]!!.jsonPrimitive.content)
    }
}
