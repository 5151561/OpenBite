package com.openbite.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClaudeVisionModelTest {
    private val request = MenuScanRequest(byteArrayOf(1, 2, 3), "zh-CN", apiKey = "sk-ant-test-key")
    private val sseHeaders = headersOf(HttpHeaders.ContentType, "text/event-stream")

    private fun model(status: HttpStatusCode, body: String, onRequest: (String, Map<String, String?>) -> Unit = { _, _ -> }) =
        ClaudeVisionModel(
            createVisionHttpClient(
                MockEngine { req ->
                    onRequest(
                        req.body.toByteArray().decodeToString(),
                        mapOf(
                            "x-api-key" to req.headers["x-api-key"],
                            "anthropic-version" to req.headers["anthropic-version"],
                        ),
                    )
                    respond(ByteReadChannel(body), status, sseHeaders)
                },
            ),
            id = AnthropicModels.HAIKU,
        )

    @Test
    fun streamsDishesFromRecordedSse() = runTest {
        var sentBody = ""
        var sentHeaders = emptyMap<String, String?>()
        val events = model(HttpStatusCode.OK, CLAUDE_SSE_FIXTURE) { body, headers ->
            sentBody = body
            sentHeaders = headers
        }.scanMenu(request).toList()

        val header = assertIs<ScanEvent.Header>(events.first())
        assertEquals(6, header.estimatedDishCount)
        val dishes = events.filterIsInstance<ScanEvent.DishScanned>().map { it.dish }
        assertEquals(listOf("焼き鳥盛り合わせ", "鯖の塩焼き", "生ビール"), dishes.map { it.nameOriginal })
        assertEquals("盐烤青花鱼", dishes[1].nameTranslated)
        assertEquals(550L, dishes[2].priceMinor)
        assertTrue(events.filterIsInstance<ScanEvent.DishStarted>().any { it.nameOriginal == "鯖の塩焼き" })
        val finished = assertIs<ScanEvent.Finished>(events.last())
        assertTrue(finished.rawOutput.contains("烤鸡肉串拼盘"))

        assertEquals("sk-ant-test-key", sentHeaders["x-api-key"])
        assertEquals(AnthropicModels.API_VERSION, sentHeaders["anthropic-version"])
        val json = lenientJson.parseToJsonElement(sentBody).jsonObject
        assertEquals(AnthropicModels.HAIKU, json["model"]!!.jsonPrimitive.content)
        assertEquals("true", json["stream"]!!.jsonPrimitive.content)
        val image = json["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject["source"]!!.jsonObject
        assertEquals("image/jpeg", image["media_type"]!!.jsonPrimitive.content)
        assertEquals("AQID", image["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun httpErrorsMapToVisionExceptions() = runTest {
        assertFailsWith<VisionException.Unauthorized> {
            model(HttpStatusCode.Unauthorized, """{"type":"error","error":{"type":"authentication_error"}}""").scanMenu(request).toList()
        }
        assertFailsWith<VisionException.RateLimited> {
            model(HttpStatusCode.TooManyRequests, "{}").scanMenu(request).toList()
        }
        assertFailsWith<VisionException.Unavailable> {
            model(HttpStatusCode.fromValue(529), "{}").scanMenu(request).toList()
        }
        val bad = assertFailsWith<VisionException.BadRequest> {
            model(HttpStatusCode.BadRequest, """{"type":"error","error":{"type":"invalid_request_error","message":"secret echo"}}""")
                .scanMenu(request).toList()
        }
        assertTrue("invalid_request_error" in bad.message.orEmpty())
        assertTrue("secret echo" !in bad.message.orEmpty())
    }

    @Test
    fun streamErrorEventMapsToUnavailable() = runTest {
        assertFailsWith<VisionException.Unavailable> {
            model(HttpStatusCode.OK, CLAUDE_SSE_OVERLOADED).scanMenu(request).toList()
        }
    }

    @Test
    fun streamCutBeforeMessageStopIsTruncated() = runTest {
        val cut = CLAUDE_SSE_FIXTURE.substringBefore("event: content_block_stop")
        val e = assertFailsWith<VisionException.Truncated> { model(HttpStatusCode.OK, cut).scanMenu(request).toList() }
        assertEquals("eof", e.reason)
    }

    @Test
    fun maxTokensStopIsTruncated() = runTest {
        val capped = CLAUDE_SSE_FIXTURE.replace("\"stop_reason\":\"end_turn\"", "\"stop_reason\":\"max_tokens\"")
        val e = assertFailsWith<VisionException.Truncated> { model(HttpStatusCode.OK, capped).scanMenu(request).toList() }
        assertEquals("max_tokens", e.reason)
    }

    @Test
    fun missingKeyFailsWithoutRequest() = runTest {
        var called = false
        assertFailsWith<VisionException.Unauthorized> {
            model(HttpStatusCode.OK, CLAUDE_SSE_FIXTURE) { _, _ -> called = true }
                .scanMenu(MenuScanRequest(byteArrayOf(), "zh-CN", apiKey = " "))
                .toList()
        }
        assertEquals(false, called)
    }

    @Test
    fun sampleModelStreamsWholeMenu() = runTest {
        val events = SampleVisionModel().scanMenu(MenuScanRequest(byteArrayOf(), "zh-CN", null)).toList()
        assertEquals(7, events.filterIsInstance<ScanEvent.DishScanned>().size)
        assertEquals(SampleVisionModel.SAMPLE_OUTPUT, assertIs<ScanEvent.Finished>(events.last()).rawOutput)
    }
}
