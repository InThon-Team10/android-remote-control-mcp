package com.danielealbano.androidremotecontrolmcp.integration

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Malformed MCP request handling")
class MalformedRequestIntegrationTest {
    @BeforeEach
    fun setUp() {
        McpIntegrationTestHelper.mockAndroidLog()
    }

    @AfterEach
    fun tearDown() {
        McpIntegrationTestHelper.unmockAndroidLog()
    }

    @Test
    fun `deeply nested JSON body is answered with a parse error instead of a server error`() =
        runTest {
            McpIntegrationTestHelper.withRawTestApplication { _ ->
                val response =
                    client.post("/mcp") {
                        header("Authorization", "Bearer ${McpIntegrationTestHelper.TEST_BEARER_TOKEN}")
                        header("Accept", "application/json, text/event-stream")
                        header("Content-Type", "application/json")
                        setBody("[".repeat(NESTING_DEPTH))
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
                assertTrue(response.bodyAsText().contains("\"code\":-32700"))
            }
        }

    private companion object {
        const val NESTING_DEPTH = 200_000
    }
}
