package com.danielealbano.androidremotecontrolmcp.integration

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("OAuth /token input handling")
class OAuthTokenInputIntegrationTest {
    @BeforeEach
    fun setUp() = McpIntegrationTestHelper.mockAndroidLog()

    @AfterEach
    fun tearDown() = McpIntegrationTestHelper.unmockAndroidLog()

    @Test
    @DisplayName("token with a JSON body returns RFC 6749 invalid_request 400")
    fun tokenJsonBodyIsInvalidRequest() =
        runTest {
            McpIntegrationTestHelper.withOAuthTestApplication { _ ->
                val resp =
                    client.post("/token") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"grant_type":"authorization_code"}""")
                    }
                assertEquals(HttpStatusCode.BadRequest, resp.status)
                val error = errorCode(resp.bodyAsText())
                assertEquals("invalid_request", error)
            }
        }

    @Test
    @DisplayName("token with no content type returns RFC 6749 invalid_request 400")
    fun tokenNoContentTypeIsInvalidRequest() =
        runTest {
            McpIntegrationTestHelper.withOAuthTestApplication { _ ->
                val resp = client.post("/token")
                assertEquals(HttpStatusCode.BadRequest, resp.status)
                val error = errorCode(resp.bodyAsText())
                assertEquals("invalid_request", error)
            }
        }

    private fun errorCode(body: String): String? {
        val obj = Json.parseToJsonElement(body).jsonObject
        return obj["error"]?.jsonPrimitive?.content
    }
}
