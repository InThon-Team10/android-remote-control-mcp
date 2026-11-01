package com.danielealbano.androidremotecontrolmcp.assistant.session

import com.danielealbano.androidremotecontrolmcp.assistant.AssistantError
import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("ChatTranscript fuzz")
class ChatTranscriptFuzzTest {
    /** Reference model: the open live item is the last appended item while no other operation closed it. */
    private class Model {
        val items = mutableListOf<ChatItem>()
        var visible = false
        var nextId = 1L
        var liveIndex: Int? = null
        var liveUser = false
        val seenIds = mutableSetOf<Long>()

        fun append(item: (Long) -> ChatItem) {
            liveIndex = null
            val created = item(nextId++)
            items += created
            seenIds += created.id
        }

        fun live(
            user: Boolean,
            text: String,
        ) {
            val open = liveIndex
            if (open != null && liveUser == user) {
                val current = items[open]
                items[open] =
                    when (current) {
                        is ChatItem.User -> current.copy(text = current.text + text)
                        is ChatItem.Assistant -> current.copy(text = current.text + text)
                        else -> current
                    }
                return
            }
            append { id -> if (user) ChatItem.User(id, text) else ChatItem.Assistant(id, text) }
            liveIndex = items.lastIndex
            liveUser = user
        }

        fun attention(): Boolean {
            val needs =
                when (val last = items.lastOrNull()) {
                    is ChatItem.Assistant, is ChatItem.Error -> true
                    is ChatItem.Notice -> last.kind !is NoticeKind.Stopped
                    is ChatItem.Confirmation -> last.answered == null
                    else -> false
                }
            return !visible && needs
        }
    }

    private fun randomItem(
        random: Random,
        id: Long,
    ): ChatItem =
        when (random.nextInt(6)) {
            0 -> ChatItem.User(id, AssistantFuzzGen.string(random))
            1 -> ChatItem.Assistant(id, AssistantFuzzGen.string(random))
            2 -> ChatItem.ToolCall(id, "c$id", "t", ToolCallStatus.entries[random.nextInt(ToolCallStatus.entries.size)])
            3 -> ChatItem.Confirmation(id, "c$id", "t", listOf(null, true, false)[random.nextInt(3)])
            4 -> ChatItem.Error(id, AssistantError.ModelUnavailable)
            else -> ChatItem.Notice(id, listOf(NoticeKind.Stopped, NoticeKind.StepLimitReached(3))[random.nextInt(2)])
        }

    @Test
    fun `random operation sequences match the reference model and keep ids unique and monotonic`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            val transcript = ChatTranscript()
            val model = Model()
            repeat(400) { step ->
                val label = "seed=$seed step=$step"
                when (random.nextInt(8)) {
                    0, 1 -> {
                        val pick = random.nextInt(6)
                        val make: (Long) -> ChatItem = { id -> randomItem(Random(seed * 1000 + pick + id), id) }
                        transcript.append(make)
                        model.append(make)
                    }

                    2, 3, 4 -> {
                        val user = random.nextBoolean()
                        val text = AssistantFuzzGen.string(random)
                        transcript.appendLive(user, text)
                        model.live(user, text)
                    }

                    5 -> {
                        transcript.endLiveTurn()
                        model.liveIndex = null
                    }

                    6 -> {
                        val visible = random.nextBoolean()
                        transcript.setChatVisible(visible)
                        model.visible = visible
                    }

                    else -> {
                        if (random.nextInt(6) == 0) {
                            transcript.clear()
                            model.items.clear()
                            model.liveIndex = null
                        } else {
                            transcript.update { it }
                        }
                    }
                }
                assertEquals(model.items, transcript.items.value, label)
                assertEquals(model.attention(), transcript.attention.value, label)
                val ids = transcript.items.value.map { it.id }
                assertTrue(ids.zipWithNext().all { (a, b) -> a < b }, "$label ids not strictly increasing: $ids")
            }
            val lastId =
                transcript.items.value
                    .lastOrNull()
                    ?.id
            if (lastId != null) assertTrue(lastId < model.nextId, "seed=$seed")
            transcript.append { ChatItem.User(it, "end") }
            assertEquals(
                model.nextId,
                transcript.items.value
                    .last()
                    .id,
                "seed=$seed ids are never reused after clear",
            )
        }
    }
}
