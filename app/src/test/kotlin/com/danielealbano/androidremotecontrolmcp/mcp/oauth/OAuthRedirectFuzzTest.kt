package com.danielealbano.androidremotecontrolmcp.mcp.oauth

import com.danielealbano.androidremotecontrolmcp.testutil.FuzzStrings
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("OAuth error redirect and pending authorize store property tests")
class OAuthRedirectFuzzTest {
    private val redirectUris =
        listOf(
            "http://localhost/cb",
            "http://localhost:6274/oauth/callback",
            "http://127.0.0.1:33418/callback?existing=1",
            "http://[::1]:8080/cb?a=b&c=d#frag",
            "https://chatgpt.com/connector/oauth/abc123",
            "http://localhost:9/cb?state=preset",
        )

    @Test
    fun `error redirects keep the target and carry error and state exactly once with any state text`() {
        val seed = 0x0A0001L
        val rnd = Random(seed)
        runBlocking {
            testApplication {
                routing {
                    get("/r") {
                        val params = call.request.queryParameters
                        call.redirectWithError(params["u"]!!, params["e"]!!, params["s"].orEmpty())
                    }
                }
                val noFollow = createClient { followRedirects = false }
                repeat(ITERATIONS) { i ->
                    val target = redirectUris[rnd.nextInt(redirectUris.size)]
                    val error = listOf("invalid_request", "access_denied", "server_error")[rnd.nextInt(3)]
                    val state = FuzzStrings.validUtf16(rnd, 40)
                    val ctx = "seed=$seed iteration=$i target=$target state='${FuzzStrings.escape(state)}'"
                    val response =
                        noFollow.get("/r") {
                            url.parameters.append("u", target)
                            url.parameters.append("e", error)
                            url.parameters.append("s", state)
                        }
                    val location = response.headers[HttpHeaders.Location]
                    assertTrue(location != null, "$ctx no Location header, status ${response.status}")
                    assertTrue(location!!.none { it.isISOControl() }, "$ctx Location has control characters")
                    val parsed = Url(location)
                    val original = Url(target)
                    assertEquals(original.protocol, parsed.protocol, ctx)
                    assertEquals(original.host, parsed.host, ctx)
                    assertEquals(original.port, parsed.port, ctx)
                    assertEquals(original.encodedPath, parsed.encodedPath, ctx)
                    assertEquals(original.fragment, parsed.fragment, ctx)
                    assertEquals(listOf(error), parsed.parameters.getAll("error"), ctx)
                    val addedStates = listOfNotNull(state.ifEmpty { null })
                    val expectedStates = original.parameters.getAll("state").orEmpty() + addedStates
                    assertEquals(expectedStates, parsed.parameters.getAll("state").orEmpty(), ctx)
                    original.parameters.entries().filter { it.key != "state" }.forEach { (key, values) ->
                        assertEquals(values, parsed.parameters.getAll(key), "$ctx $key")
                    }
                }
            }
        }
    }

    @Test
    fun `pending authorize requests are single use, expire at their deadline and unknown ids never resolve`() =
        runBlocking {
            val seed = 0x0A0002L
            val rnd = Random(seed)
            val store = PendingAuthorizeStore()
            val live = HashMap<String, PendingAuthorizeRequest>()
            var now = 0L
            repeat(ITERATIONS) { i ->
                now += rnd.nextLong(0, 50)
                val id = "id" + rnd.nextInt(20)
                val ctx = "seed=$seed iteration=$i id=$id now=$now"
                when (rnd.nextInt(3)) {
                    0 -> {
                        val request =
                            PendingAuthorizeRequest(
                                "c",
                                "http://localhost/cb",
                                "ch",
                                "r",
                                "mcp",
                                "s$i",
                                now + rnd.nextLong(1, 200),
                            )
                        store.put(id, request, now)
                        live.entries.removeAll { it.value.expiresAtMs <= now }
                        live[id] = request
                    }

                    1 -> {
                        val expected = live.remove(id)?.takeIf { it.expiresAtMs > now }
                        live.entries.removeAll { it.value.expiresAtMs <= now }
                        assertEquals(expected, store.consume(id, now), ctx)
                        assertNull(store.consume(id, now), "$ctx second consume")
                    }

                    else -> {
                        assertNull(store.consume(FuzzStrings.random(rnd, 12) + "#", now), ctx)
                    }
                }
            }
        }

    private companion object {
        const val ITERATIONS = 400
    }
}
