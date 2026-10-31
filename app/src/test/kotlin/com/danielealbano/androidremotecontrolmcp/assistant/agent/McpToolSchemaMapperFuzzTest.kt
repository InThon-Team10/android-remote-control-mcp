package com.danielealbano.androidremotecontrolmcp.assistant.agent

import com.danielealbano.androidremotecontrolmcp.testutil.AssistantFuzzGen
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random
import io.modelcontextprotocol.kotlin.sdk.types.Tool as McpTool

@DisplayName("McpToolSchemaMapper fuzz")
class McpToolSchemaMapperFuzzTest {
    private val typeNames = listOf("string", "number", "integer", "boolean", "array", "object", "null", "weird", "")

    private fun fragment(
        random: Random,
        depth: Int,
    ): JsonElement {
        val entries = LinkedHashMap<String, JsonElement>()
        if (random.nextInt(5) != 0) entries["type"] = JsonPrimitive(typeNames[random.nextInt(typeNames.size)])
        if (random.nextInt(3) == 0) {
            entries["enum"] = JsonArray(List(random.nextInt(4)) { AssistantFuzzGen.primitive(random) })
        }
        if (random.nextInt(3) == 0) entries["description"] = JsonPrimitive(AssistantFuzzGen.string(random, 30))
        if (depth > 0) {
            if (random.nextInt(3) == 0) entries["items"] = fragment(random, depth - 1)
            if (random.nextInt(3) == 0) {
                entries["properties"] =
                    JsonObject(mapOf("p" to fragment(random, depth - 1), "q" to fragment(random, depth - 1)))
            }
            for (keyword in listOf("oneOf", "anyOf")) {
                if (random.nextInt(4) == 0) {
                    entries[keyword] = JsonArray(List(random.nextInt(3)) { fragment(random, depth - 1) })
                }
            }
            if (random.nextInt(5) == 0) {
                entries["\$ref"] = JsonPrimitive("#/\$defs/" + AssistantFuzzGen.identifier(random))
            }
            if (random.nextInt(5) == 0) entries["additionalProperties"] = JsonPrimitive(random.nextBoolean())
        }
        if (random.nextInt(6) == 0) entries[AssistantFuzzGen.string(random, 6)] = JsonNull
        return JsonObject(entries)
    }

    private fun randomTool(random: Random): McpTool {
        val properties =
            when (random.nextInt(4)) {
                0 -> {
                    null
                }

                1 -> {
                    JsonObject(emptyMap())
                }

                else -> {
                    JsonObject(
                        List(random.nextInt(6)) { AssistantFuzzGen.string(random, 8) to fragment(random, 3) }.toMap(),
                    )
                }
            }
        val required =
            when (random.nextInt(3)) {
                0 -> null
                1 -> emptyList()
                else -> List(random.nextInt(4)) { AssistantFuzzGen.string(random, 8) }
            }
        return McpTool(
            name = AssistantFuzzGen.identifier(random),
            description = if (random.nextBoolean()) null else AssistantFuzzGen.string(random, 60),
            inputSchema = ToolSchema(properties = properties, required = required),
        )
    }

    @Test
    fun `random schemas map without throwing and keep properties verbatim`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            repeat(150) { index ->
                val tool = randomTool(random)
                val declaration = McpToolSchemaMapper.toDeclaration(tool)
                val label = "seed=$seed index=$index"
                assertEquals(tool.name, declaration.name().get(), label)
                assertEquals(tool.description.orEmpty(), declaration.description().get(), label)
                val schema = declaration.parametersJsonSchema().get() as Map<*, *>
                assertEquals("object", schema["type"], label)
                assertEquals(tool.inputSchema.properties?.size ?: 0, (schema["properties"] as Map<*, *>).size, label)
                val expectedRequired = tool.inputSchema.required.orEmpty()
                val expectedKeys = setOf("type", "properties") + expectedRequired.take(1).map { "required" }
                assertEquals(expectedKeys, schema.keys, label)
                if (expectedRequired.isNotEmpty()) assertEquals(expectedRequired, schema["required"], label)
                assertTrue(declaration.toJson().isNotEmpty(), label)
            }
        }
    }

    @Test
    fun `a tool list keeps order and count`() {
        for (seed in AssistantFuzzGen.SEEDS) {
            val random = Random(seed)
            val tools = List(random.nextInt(60)) { randomTool(random) }
            val declarations = McpToolSchemaMapper.toGeminiTool(tools).functionDeclarations().orElse(emptyList())
            assertEquals(tools.map { it.name }, declarations.map { it.name().get() }, "seed=$seed")
        }
    }
}
