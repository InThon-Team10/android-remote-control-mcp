package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import com.google.genai.types.Blob
import com.google.genai.types.BlockedReason
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FinishReason
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponsePromptFeedback
import com.google.genai.types.Part
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("GeminiResponseReader fuzz")
class GeminiResponseReaderFuzzTest {
    private val blockingFinish =
        setOf(
            FinishReason.Known.SAFETY,
            FinishReason.Known.RECITATION,
            FinishReason.Known.BLOCKLIST,
            FinishReason.Known.PROHIBITED_CONTENT,
            FinishReason.Known.SPII,
            FinishReason.Known.IMAGE_SAFETY,
            FinishReason.Known.LANGUAGE,
        )

    private class Generated(
        val response: GenerateContentResponse,
        val promptBlocked: Boolean,
        val parts: List<Part>?,
        val finish: FinishReason.Known?,
    )

    private fun randomPart(random: Random): Part {
        val builder = Part.builder()
        when (random.nextInt(6)) {
            0 -> {
                builder.text(AssistantFuzzGen.string(random))
            }

            1 -> {
                builder.text(AssistantFuzzGen.string(random)).thought(true)
            }

            2 -> {
                builder.thought(true)
            }

            3 -> {
                builder.functionCall(
                    FunctionCall
                        .builder()
                        .name(AssistantFuzzGen.identifier(random))
                        .args(mapOf("a" to 1))
                        .build(),
                )
            }

            4 -> {
                builder.inlineData(
                    Blob
                        .builder()
                        .data(ByteArray(random.nextInt(8)))
                        .mimeType("audio/pcm")
                        .build(),
                )
            }

            else -> {
                builder.text(AssistantFuzzGen.string(random)).thought(false)
            }
        }
        if (random.nextInt(4) == 0) builder.thoughtSignature(ByteArray(random.nextInt(5)))
        return builder.build()
    }

    /** Adds a random prompt feedback (blocked, empty or none); returns whether it blocks the prompt. */
    private fun applyFeedback(
        random: Random,
        response: GenerateContentResponse.Builder,
    ): Boolean {
        val reasons = BlockedReason.Known.values()
        if (random.nextInt(8) == 0) {
            val reason = reasons[random.nextInt(reasons.size)]
            val feedback = GenerateContentResponsePromptFeedback.builder().blockReason(reason)
            response.promptFeedback(feedback.build())
            return true
        }
        if (random.nextInt(4) == 0) response.promptFeedback(GenerateContentResponsePromptFeedback.builder().build())
        return false
    }

    private fun generate(random: Random): Generated {
        val finishes = FinishReason.Known.values()
        val response = GenerateContentResponse.builder()
        val blocked = applyFeedback(random, response)
        var parts: List<Part>? = null
        var finish: FinishReason.Known? = null
        when (random.nextInt(5)) {
            0 -> {
                Unit
            }

            1 -> {
                response.candidates(emptyList())
            }

            else -> {
                val candidate = Candidate.builder()
                if (random.nextInt(5) != 0) {
                    finish = finishes[random.nextInt(finishes.size)]
                    candidate.finishReason(finish)
                }
                when (random.nextInt(4)) {
                    0 -> {
                        Unit
                    }

                    1 -> {
                        parts = emptyList()
                        candidate.content(
                            Content
                                .builder()
                                .role("model")
                                .parts(parts)
                                .build(),
                        )
                    }

                    2 -> {
                        candidate.content(Content.builder().role("model").build())
                    }

                    else -> {
                        parts = List(1 + random.nextInt(5)) { randomPart(random) }
                        candidate.content(
                            Content
                                .builder()
                                .role("model")
                                .parts(parts)
                                .build(),
                        )
                    }
                }
                response.candidates(listOf(candidate.build(), Candidate.builder().build()))
            }
        }
        return Generated(response.build(), blocked, parts, finish)
    }

    @Test
    fun `modelContent returns the verbatim content or a typed failure for random responses`() {
        var returned = 0
        var blockedByFinish = 0
        var failed = 0
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(250) { index ->
                val generated = generate(random)
                val label = "seed=$seed index=$index"
                val result = runCatching { GeminiResponseReader.modelContent(generated.response) }
                val error = result.exceptionOrNull()
                if (error != null && error !is AgentFailure) fail<Unit>("$label raw exception $error")
                val expectContent = !generated.promptBlocked && !generated.parts.isNullOrEmpty()
                if (expectContent) {
                    returned++
                    assertSame(
                        generated.response
                            .candidates()
                            .get()[0]
                            .content()
                            .get(),
                        result.getOrThrow(),
                        "$label content must be returned verbatim",
                    )
                    return@repeat
                }
                failed++
                val failure = (error as AgentFailure).error
                val expectBlocked =
                    generated.promptBlocked ||
                        (generated.finish in blockingFinish && generated.response.candidates().isPresent)
                if (expectBlocked) blockedByFinish++
                if (expectBlocked) {
                    assertTrue(failure is AssistantError.GeminiBlocked, "$label expected blocked, was $failure")
                } else {
                    assertTrue(failure is AssistantError.GeminiRequestFailed, "$label expected failure, was $failure")
                }
            }
        }
        assertTrue(returned > 0 && failed > 0 && blockedByFinish > 0, "coverage $returned/$failed/$blockedByFinish")
    }

    @Test
    fun `texts and functionCalls partition random parts consistently`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(250) { index ->
                val parts = List(random.nextInt(8)) { randomPart(random) }
                val content =
                    Content
                        .builder()
                        .role("model")
                        .parts(parts)
                        .build()
                val label = "seed=$seed index=$index"
                val texts = GeminiResponseReader.texts(content)
                val expected =
                    parts
                        .filter { it.thought().orElse(false) != true }
                        .mapNotNull { it.text().orElse(null) }
                        .filter { it.isNotBlank() }
                assertEquals(expected, texts, label)
                assertFalse(texts.any { it.isBlank() }, label)
                val calls = GeminiResponseReader.functionCalls(content)
                assertEquals(parts.count { it.functionCall().isPresent }, calls.size, label)
            }
        }
    }

    @Test
    fun `content without parts yields empty texts and calls`() {
        val content = Content.builder().role("model").build()

        assertTrue(GeminiResponseReader.texts(content).isEmpty())
        assertTrue(GeminiResponseReader.functionCalls(content).isEmpty())
    }
}
