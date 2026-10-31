package com.danielealbano.androidremotecontrolmcp.mcp.tools

import com.danielealbano.androidremotecontrolmcp.mcp.McpToolException
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("McpToolUtils")
class McpToolUtilsTest {
    // ─────────────────────────────────────────────────────────────────────
    // requireFloat
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireFloat")
    inner class RequireFloatTests {
        @Test
        @DisplayName("returns float for integer value")
        fun returnsFloatForIntegerValue() {
            val params = buildJsonObject { put("x", 500) }
            assertEquals(500f, McpToolUtils.requireFloat(params, "x"))
        }

        @Test
        @DisplayName("returns float for floating-point value")
        fun returnsFloatForFloatingPointValue() {
            val params = buildJsonObject { put("x", 500.5) }
            assertEquals(500.5f, McpToolUtils.requireFloat(params, "x"))
        }

        @Test
        @DisplayName("rejects string-encoded number")
        fun rejectsStringEncodedNumber() {
            val params = buildJsonObject { put("x", "500") }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireFloat(params, "x")
                }
            assertTrue(exception.message!!.contains("got string"))
        }

        @Test
        @DisplayName("rejects missing parameter")
        fun rejectsMissingParameter() {
            val params = buildJsonObject { put("y", 100) }

            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireFloat(params, "x")
            }
        }

        @Test
        @DisplayName("rejects null params")
        fun rejectsNullParams() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireFloat(null, "x")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // requireInt
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireInt")
    inner class RequireIntTests {
        @Test
        @DisplayName("returns integer value")
        fun returnsIntegerValue() {
            val params = buildJsonObject { put("x", 42) }
            assertEquals(42, McpToolUtils.requireInt(params, "x"))
        }

        @Test
        @DisplayName("throws for missing param")
        fun throwsForMissingParam() {
            val params = buildJsonObject { put("y", 100) }

            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireInt(params, "x")
            }
        }

        @Test
        @DisplayName("throws for null param")
        fun throwsForNullParam() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireInt(null, "x")
            }
        }

        @Test
        @DisplayName("throws for string-encoded number")
        fun throwsForStringEncodedNumber() {
            val params = buildJsonObject { put("x", "42") }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireInt(params, "x")
                }
            assertTrue(exception.message!!.contains("got string"))
        }

        @Test
        @DisplayName("throws for float value")
        fun throwsForFloatValue() {
            val params = buildJsonObject { put("x", 3.5) }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireInt(params, "x")
                }
            assertTrue(exception.message!!.contains("integer"))
        }

        @Test
        @DisplayName("accepts integer-equivalent float")
        fun acceptsIntegerEquivalentFloat() {
            val params = buildJsonObject { put("x", 5.0) }
            assertEquals(5, McpToolUtils.requireInt(params, "x"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // optionalFloat
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("optionalFloat")
    inner class OptionalFloatTests {
        @Test
        @DisplayName("returns default when parameter is absent")
        fun returnsDefaultWhenAbsent() {
            val params = buildJsonObject {}
            assertEquals(42f, McpToolUtils.optionalFloat(params, "x", 42f))
        }

        @Test
        @DisplayName("returns parsed value when present")
        fun returnsParsedValueWhenPresent() {
            val params = buildJsonObject { put("x", 100.5) }
            assertEquals(100.5f, McpToolUtils.optionalFloat(params, "x", 42f))
        }

        @Test
        @DisplayName("rejects string-encoded number")
        fun rejectsStringEncodedNumber() {
            val params = buildJsonObject { put("x", "100") }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.optionalFloat(params, "x", 42f)
                }
            assertTrue(exception.message!!.contains("got string"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // optionalLong
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("optionalLong")
    inner class OptionalLongTests {
        @Test
        @DisplayName("returns default when parameter is absent")
        fun returnsDefaultWhenAbsent() {
            val params = buildJsonObject {}
            assertEquals(300L, McpToolUtils.optionalLong(params, "duration", 300L))
        }

        @Test
        @DisplayName("returns parsed value for integer")
        fun returnsParsedValueForInteger() {
            val params = buildJsonObject { put("duration", 500) }
            assertEquals(500L, McpToolUtils.optionalLong(params, "duration", 300L))
        }

        @Test
        @DisplayName("accepts integer-equivalent float (1.0 as 1)")
        fun acceptsIntegerEquivalentFloat() {
            val params = buildJsonObject { put("duration", 500.0) }
            assertEquals(500L, McpToolUtils.optionalLong(params, "duration", 300L))
        }

        @Test
        @DisplayName("keeps full precision above 2^53")
        fun keepsFullPrecisionAboveTwoPow53() {
            val params = buildJsonObject { put("duration", 9007199254740993L) }
            assertEquals(9007199254740993L, McpToolUtils.optionalLong(params, "duration", 300L))
            assertEquals(9007199254740993L, McpToolUtils.requireLong(params, "duration"))
        }

        @Test
        @DisplayName("rejects fractional value")
        fun rejectsFractionalValue() {
            val params = buildJsonObject { put("duration", 1.5) }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.optionalLong(params, "duration", 300L)
                }
            assertTrue(exception.message!!.contains("integer"))
        }

        @Test
        @DisplayName("rejects string-encoded number")
        fun rejectsStringEncodedNumber() {
            val params = buildJsonObject { put("duration", "500") }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.optionalLong(params, "duration", 300L)
                }
            assertTrue(exception.message!!.contains("got string"))
        }

        @Test
        @DisplayName("rejects values outside long range and non-finite literals")
        fun rejectsOutOfLongRange() {
            for (literal in listOf("9223372036854775808", "-9223372036854775809", "1e19", "Infinity", "NaN")) {
                val params = Json.parseToJsonElement("{\"duration\": $literal}") as JsonObject
                assertThrows<McpToolException.InvalidParams>(literal) {
                    McpToolUtils.optionalLong(params, "duration", 300L)
                }
            }
        }

        @Test
        @DisplayName("accepts Long.MIN_VALUE and Long.MAX_VALUE")
        fun acceptsLongBounds() {
            val min = Json.parseToJsonElement("{\"duration\": -9223372036854775808}") as JsonObject
            val max = Json.parseToJsonElement("{\"duration\": 9223372036854775807}") as JsonObject
            assertEquals(Long.MIN_VALUE, McpToolUtils.optionalLong(min, "duration", 300L))
            assertEquals(Long.MAX_VALUE, McpToolUtils.optionalLong(max, "duration", 300L))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // requireLong
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireLong")
    inner class RequireLongTests {
        @Test
        @DisplayName("valid integer returns Long")
        fun validIntegerReturnsLong() {
            val params = buildJsonObject { put("duration", 60000) }
            assertEquals(60000L, McpToolUtils.requireLong(params, "duration"))
        }

        @Test
        @DisplayName("missing param throws InvalidParams")
        fun missingParamThrowsInvalidParams() {
            val params = buildJsonObject { put("other", 100) }

            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireLong(params, "duration")
            }
        }

        @Test
        @DisplayName("null params throws InvalidParams")
        fun nullParamsThrowsInvalidParams() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireLong(null, "duration")
            }
        }

        @Test
        @DisplayName("non-number throws InvalidParams")
        fun nonNumberThrowsInvalidParams() {
            val params = buildJsonObject { put("duration", true) }

            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireLong(params, "duration")
            }
        }

        @Test
        @DisplayName("decimal throws InvalidParams")
        fun decimalThrowsInvalidParams() {
            val params = buildJsonObject { put("duration", 3.5) }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireLong(params, "duration")
                }
            assertTrue(exception.message!!.contains("integer"))
        }

        @Test
        @DisplayName("rejects string-encoded number")
        fun rejectsStringEncodedNumber() {
            val params = buildJsonObject { put("duration", "60000") }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireLong(params, "duration")
                }
            assertTrue(exception.message!!.contains("got string"))
        }

        @Test
        @DisplayName("rejects values outside long range and non-finite literals")
        fun rejectsOutOfLongRange() {
            for (literal in listOf("9223372036854775808", "-9223372036854775809", "1e19", "Infinity", "NaN")) {
                val params = Json.parseToJsonElement("{\"duration\": $literal}") as JsonObject
                assertThrows<McpToolException.InvalidParams>(literal) {
                    McpToolUtils.requireLong(params, "duration")
                }
            }
        }

        @Test
        @DisplayName("accepts Long.MIN_VALUE and Long.MAX_VALUE")
        fun acceptsLongBounds() {
            val min = Json.parseToJsonElement("{\"duration\": -9223372036854775808}") as JsonObject
            val max = Json.parseToJsonElement("{\"duration\": 9223372036854775807}") as JsonObject
            assertEquals(Long.MIN_VALUE, McpToolUtils.requireLong(min, "duration"))
            assertEquals(Long.MAX_VALUE, McpToolUtils.requireLong(max, "duration"))
        }

        @Test
        @DisplayName("accepts integer-equivalent float")
        fun acceptsIntegerEquivalentFloat() {
            val params = buildJsonObject { put("duration", 5.0) }
            assertEquals(5L, McpToolUtils.requireLong(params, "duration"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // requireString
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("requireString")
    inner class RequireStringTests {
        @Test
        @DisplayName("returns string for valid string parameter")
        fun returnsStringForValidParameter() {
            val params = buildJsonObject { put("direction", "up") }
            assertEquals("up", McpToolUtils.requireString(params, "direction"))
        }

        @Test
        @DisplayName("rejects numeric primitive as string")
        fun rejectsNumericPrimitiveAsString() {
            val params = buildJsonObject { put("direction", 123) }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireString(params, "direction")
                }
            assertTrue(exception.message!!.contains("must be a string"))
        }

        @Test
        @DisplayName("rejects boolean primitive as string")
        fun rejectsBooleanPrimitiveAsString() {
            val params = buildJsonObject { put("direction", true) }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.requireString(params, "direction")
                }
            assertTrue(exception.message!!.contains("must be a string"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // optionalString
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("optionalString")
    inner class OptionalStringTests {
        @Test
        @DisplayName("returns default when parameter is absent")
        fun returnsDefaultWhenAbsent() {
            val params = buildJsonObject {}
            assertEquals("medium", McpToolUtils.optionalString(params, "amount", "medium"))
        }

        @Test
        @DisplayName("returns parsed value when present")
        fun returnsParsedValueWhenPresent() {
            val params = buildJsonObject { put("amount", "large") }
            assertEquals("large", McpToolUtils.optionalString(params, "amount", "medium"))
        }

        @Test
        @DisplayName("rejects numeric primitive as string")
        fun rejectsNumericPrimitiveAsString() {
            val params = buildJsonObject { put("amount", 42) }

            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.optionalString(params, "amount", "medium")
                }
            assertTrue(exception.message!!.contains("must be a string"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // validateNonNegative
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateNonNegative")
    inner class ValidateNonNegativeTests {
        @Test
        @DisplayName("accepts zero")
        fun acceptsZero() {
            McpToolUtils.validateNonNegative(0f, "x")
        }

        @Test
        @DisplayName("accepts positive value")
        fun acceptsPositiveValue() {
            McpToolUtils.validateNonNegative(100f, "x")
        }

        @Test
        @DisplayName("rejects negative value")
        fun rejectsNegativeValue() {
            val exception =
                assertThrows<McpToolException.InvalidParams> {
                    McpToolUtils.validateNonNegative(-1f, "x")
                }
            assertTrue(exception.message!!.contains(">= 0"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // validatePositiveRange
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validatePositiveRange")
    inner class ValidatePositiveRangeTests {
        @Test
        @DisplayName("accepts value within range")
        fun acceptsValueWithinRange() {
            McpToolUtils.validatePositiveRange(500L, "duration", 60000L)
        }

        @Test
        @DisplayName("accepts max value")
        fun acceptsMaxValue() {
            McpToolUtils.validatePositiveRange(60000L, "duration", 60000L)
        }

        @Test
        @DisplayName("rejects zero")
        fun rejectsZero() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.validatePositiveRange(0L, "duration", 60000L)
            }
        }

        @Test
        @DisplayName("rejects value exceeding max")
        fun rejectsValueExceedingMax() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.validatePositiveRange(60001L, "duration", 60000L)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // handleActionResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("handleActionResult")
    inner class HandleActionResultTests {
        @Test
        @DisplayName("returns text content on success")
        fun returnsTextContentOnSuccess() {
            val result = McpToolUtils.handleActionResult(Result.success(Unit), "Action done")
            assertEquals(1, result.content.size)
            val textContent = result.content[0] as TextContent
            assertEquals("Action done", textContent.text)
        }

        @Test
        @DisplayName("throws PermissionDenied for service not available")
        fun throwsPermissionDeniedForServiceNotAvailable() {
            val result = Result.failure<Unit>(IllegalStateException("Service is not available"))

            assertThrows<McpToolException.PermissionDenied> {
                McpToolUtils.handleActionResult(result, "Action done")
            }
        }

        @Test
        @DisplayName("throws ActionFailed for other errors")
        fun throwsActionFailedForOtherErrors() {
            val result = Result.failure<Unit>(RuntimeException("Something broke"))

            val exception =
                assertThrows<McpToolException.ActionFailed> {
                    McpToolUtils.handleActionResult(result, "Action done")
                }
            assertTrue(exception.message!!.contains("Something broke"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // textResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("textResult")
    inner class TextResultTests {
        @Test
        @DisplayName("returns CallToolResult with TextContent")
        fun returnsCallToolResultWithTextContent() {
            val result = McpToolUtils.textResult("Hello world")
            assertEquals(1, result.content.size)
            val textContent = result.content[0] as TextContent
            assertEquals("Hello world", textContent.text)
        }

        @Test
        @DisplayName("returns CallToolResult with empty text")
        fun returnsCallToolResultWithEmptyText() {
            val result = McpToolUtils.textResult("")
            assertEquals(1, result.content.size)
            val textContent = result.content[0] as TextContent
            assertEquals("", textContent.text)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // imageResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("imageResult")
    inner class ImageResultTests {
        @Test
        @DisplayName("returns CallToolResult with ImageContent")
        fun returnsCallToolResultWithImageContent() {
            val result = McpToolUtils.imageResult(data = "base64data", mimeType = "image/jpeg")
            assertEquals(1, result.content.size)
            val imageContent = result.content[0] as ImageContent
            assertEquals("base64data", imageContent.data)
            assertEquals("image/jpeg", imageContent.mimeType)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // textAndImageResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("textAndImageResult")
    inner class TextAndImageResultTests {
        @Test
        @DisplayName("returns CallToolResult with TextContent and ImageContent")
        fun returnsCallToolResultWithTextAndImageContent() {
            val result = McpToolUtils.textAndImageResult("hello", "base64data", "image/jpeg")
            assertEquals(2, result.content.size)
            val text = result.content[0] as TextContent
            val image = result.content[1] as ImageContent
            assertEquals("hello", text.text)
            assertEquals("base64data", image.data)
            assertEquals("image/jpeg", image.mimeType)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // buildToolNamePrefix
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildToolNamePrefix")
    inner class BuildToolNamePrefixTests {
        @Test
        @DisplayName("returns android_ for empty slug")
        fun returnsAndroidPrefixForEmptySlug() {
            assertEquals("android_", McpToolUtils.buildToolNamePrefix(""))
        }

        @Test
        @DisplayName("returns android_slug_ for non-empty slug")
        fun returnsAndroidSlugPrefixForNonEmptySlug() {
            assertEquals("android_pixel7_", McpToolUtils.buildToolNamePrefix("pixel7"))
        }

        @Test
        @DisplayName("handles slug with underscores")
        fun handlesSlugWithUnderscores() {
            assertEquals("android_work_phone_", McpToolUtils.buildToolNamePrefix("work_phone"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // untrustedTextResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("untrustedTextResult")
    inner class UntrustedTextResultTests {
        @Test
        @DisplayName("prepends warning before content")
        fun prependsWarningBeforeContent() {
            val result = McpToolUtils.untrustedTextResult("some content")
            assertEquals(1, result.content.size)
            val text = (result.content[0] as TextContent).text
            assertTrue(text.startsWith(McpToolUtils.UNTRUSTED_CONTENT_WARNING))
            assertTrue(text.endsWith("some content"))
        }

        @Test
        @DisplayName("text starts with warning then newline")
        fun textStartsWithWarningThenNewline() {
            val result = McpToolUtils.untrustedTextResult("data")
            val text = (result.content[0] as TextContent).text
            assertEquals("${McpToolUtils.UNTRUSTED_CONTENT_WARNING}\ndata", text)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // untrustedTextAndImageResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("untrustedTextAndImageResult")
    inner class UntrustedTextAndImageResultTests {
        @Test
        @DisplayName("prepends warning to text and includes image")
        fun prependsWarningToTextAndIncludesImage() {
            val result = McpToolUtils.untrustedTextAndImageResult("info", "imgdata", "image/png")
            assertEquals(2, result.content.size)
            val text = result.content[0] as TextContent
            val image = result.content[1] as ImageContent
            assertTrue(text.text.startsWith(McpToolUtils.UNTRUSTED_CONTENT_WARNING))
            assertTrue(text.text.endsWith("info"))
            assertEquals("imgdata", image.data)
            assertEquals("image/png", image.mimeType)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // untrustedImageResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("untrustedImageResult")
    inner class UntrustedImageResultTests {
        @Test
        @DisplayName("adds warning as separate text content before image")
        fun addsWarningAsSeparateTextBeforeImage() {
            val result = McpToolUtils.untrustedImageResult("imgdata", "image/jpeg")
            assertEquals(2, result.content.size)
            val text = result.content[0] as TextContent
            val image = result.content[1] as ImageContent
            assertEquals(McpToolUtils.UNTRUSTED_CONTENT_WARNING, text.text)
            assertEquals("imgdata", image.data)
            assertEquals("image/jpeg", image.mimeType)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // untrustedResult
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("untrustedResult")
    inner class UntrustedResultTests {
        @Test
        @DisplayName("prepends warning as first content block, preserving the rest in order")
        fun prependsWarningFirst() {
            val first = TextContent("first")
            val image = ImageContent(data = "imgdata", mimeType = "image/png")
            val result = McpToolUtils.untrustedResult(listOf(first, image))

            assertEquals(3, result.content.size)
            assertEquals(McpToolUtils.UNTRUSTED_CONTENT_WARNING, (result.content[0] as TextContent).text)
            assertEquals("first", (result.content[1] as TextContent).text)
            val resultImage = result.content[2] as ImageContent
            assertEquals("imgdata", resultImage.data)
            assertEquals("image/png", resultImage.mimeType)
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // UNTRUSTED_CONTENT_WARNING constant
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UNTRUSTED_CONTENT_WARNING")
    inner class UntrustedContentWarningTests {
        @Test
        @DisplayName("is not empty")
        fun isNotEmpty() {
            assertTrue(McpToolUtils.UNTRUSTED_CONTENT_WARNING.isNotBlank())
        }

        @Test
        @DisplayName("does not use note prefix")
        fun doesNotUseNotePrefix() {
            assertTrue(!McpToolUtils.UNTRUSTED_CONTENT_WARNING.lowercase().startsWith("note:"))
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // buildServerName
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildServerName")
    inner class BuildServerNameTests {
        @Test
        @DisplayName("returns default name for empty slug")
        fun returnsDefaultNameForEmptySlug() {
            assertEquals("android-remote-control-mcp", McpToolUtils.buildServerName(""))
        }

        @Test
        @DisplayName("includes slug in server name")
        fun includesSlugInServerName() {
            assertEquals("android-remote-control-mcp-pixel7", McpToolUtils.buildServerName("pixel7"))
        }

        @Test
        @DisplayName("handles slug with underscores")
        fun handlesSlugWithUnderscores() {
            assertEquals("android-remote-control-mcp-work_phone", McpToolUtils.buildServerName("work_phone"))
        }
    }

    @Nested
    @DisplayName("exact integer parsing")
    inner class ExactIntegerTests {
        private fun literal(content: String): JsonObject = Json.parseToJsonElement("""{"v":$content}""") as JsonObject

        @Test
        @DisplayName("requireLong rejects an exponent that overflows BigDecimal with InvalidParams")
        fun requireLongHugeNegativeExponent() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireLong(literal("1.5E-2147483646"), "v")
            }
        }

        @Test
        @DisplayName("optionalLong rejects an exponent that overflows BigDecimal with InvalidParams")
        fun optionalLongHugeNegativeExponent() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.optionalLong(literal("1.5E-99999999999"), "v", 0L)
            }
        }

        @Test
        @DisplayName("requireInt rejects a value that only rounds to an integer as a double")
        fun requireIntRejectsNonIntegerThatRoundsToInteger() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireInt(literal("2147483647.0000000000000001"), "v")
            }
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.requireInt(literal("1e-400"), "v")
            }
        }

        @Test
        @DisplayName("optionalInt rejects a value that only rounds to an integer as a double")
        fun optionalIntRejectsNonIntegerThatRoundsToInteger() {
            assertThrows<McpToolException.InvalidParams> {
                McpToolUtils.optionalInt(literal("5.0000000000000000001"), "v", 0)
            }
        }

        @Test
        @DisplayName("int extractors still accept integer-valued floats and exponent forms")
        fun intExtractorsAcceptIntegerValuedForms() {
            assertEquals(5, McpToolUtils.requireInt(literal("5.0"), "v"))
            assertEquals(100, McpToolUtils.optionalInt(literal("1e2"), "v", 0))
            assertEquals(Int.MIN_VALUE, McpToolUtils.requireInt(literal("-2147483648"), "v"))
        }
    }
}
