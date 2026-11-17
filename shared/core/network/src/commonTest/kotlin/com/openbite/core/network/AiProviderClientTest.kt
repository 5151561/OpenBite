package com.openbite.core.network

import com.openbite.core.model.AiProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiProviderClientTest {
    private val sse = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val request = MenuScanRequest(byteArrayOf(1, 2, 3), "zh-CN", apiKey = "sk-test")

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        AiProviderClient(createVisionHttpClient(MockEngine(handler)))

    /** 把一段 NDJSON 切成几块，包成 OpenAI 的 SSE 流。 */
    private fun openAiSse(text: String, finish: String = "stop", done: Boolean = true): String = buildString {
        text.chunked(17).forEach { piece ->
            val escaped = piece.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            append("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$escaped\"},\"finish_reason\":null}]}\n\n")
        }
        append("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"$finish\"}]}\n\n")
        if (done) append("data: [DONE]\n\n")
    }

    private val openai = ProviderEndpoint(AiProvider.OPENAI, "https://api.openai.com/v1")

    @Test
    fun openAiCompatStreamsDishes() = runTest {
        var url = ""
        var auth: String? = null
        var body = ""
        val events = client { req ->
            url = req.url.toString()
            auth = req.headers[HttpHeaders.Authorization]
            body = req.body.toByteArray().decodeToString()
            respond(ByteReadChannel(openAiSse(SampleVisionModel.SAMPLE_OUTPUT)), HttpStatusCode.OK, sse)
        }.modelFor(openai, "vision-x").scanMenu(request).toList()

        assertEquals(7, events.filterIsInstance<ScanEvent.DishScanned>().size)
        assertEquals(SampleVisionModel.SAMPLE_OUTPUT, assertIs<ScanEvent.Finished>(events.last()).rawOutput)
        assertEquals("https://api.openai.com/v1/chat/completions", url)
        assertEquals("Bearer sk-test", auth)
        val sent = lenientJson.parseToJsonElement(body).jsonObject
        assertEquals("vision-x", sent["model"]!!.jsonPrimitive.content)
        assertNull(sent["max_tokens"])
        val image = sent["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("data:image/jpeg;base64,AQID", image["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun finishReasonWithoutDoneStillCounts() = runTest {
        val events = client { respond(ByteReadChannel(openAiSse(SampleVisionModel.SAMPLE_OUTPUT, done = false)), HttpStatusCode.OK, sse) }
            .modelFor(openai, "m").scanMenu(request).toList()
        assertEquals(7, events.filterIsInstance<ScanEvent.DishScanned>().size)
    }

    @Test
    fun lengthFinishAndCutStreamAreTruncated() = runTest {
        val capped = assertFailsWith<VisionException.Truncated> {
            client { respond(ByteReadChannel(openAiSse("{\"type\":\"menu\"}\n", finish = "length")), HttpStatusCode.OK, sse) }
                .modelFor(openai, "m").scanMenu(request).toList()
        }
        assertEquals("max_tokens", capped.reason)
        val cut = openAiSse(SampleVisionModel.SAMPLE_OUTPUT).substringBefore("\"finish_reason\":\"stop\"").substringBeforeLast("data:")
        val eof = assertFailsWith<VisionException.Truncated> {
            client { respond(ByteReadChannel(cut), HttpStatusCode.OK, sse) }.modelFor(openai, "m").scanMenu(request).toList()
        }
        assertEquals("eof", eof.reason)
    }

    @Test
    fun openAiErrorsMap() = runTest {
        suspend fun fail(status: HttpStatusCode, body: String) = runCatching {
            client { respond(ByteReadChannel(body), status, json) }.modelFor(openai, "m").scanMenu(request).toList()
        }.exceptionOrNull()

        assertIs<VisionException.Unauthorized>(fail(HttpStatusCode.Unauthorized, """{"error":{"code":"invalid_api_key"}}"""))
        assertIs<VisionException.ModelNotFound>(fail(HttpStatusCode.NotFound, """{"error":{"code":"model_not_found"}}"""))
        assertIs<VisionException.ModelNotFound>(fail(HttpStatusCode.BadRequest, """{"error":{"code":"model_not_found","message":"x"}}"""))
        val bad = assertIs<VisionException.BadRequest>(
            fail(HttpStatusCode.BadRequest, """[{"error":{"code":400,"message":"secret echo","status":"INVALID_ARGUMENT"}}]"""),
        )
        assertTrue("INVALID_ARGUMENT" in bad.message.orEmpty())
        assertTrue("secret echo" !in bad.message.orEmpty())
    }

    @Test
    fun listsOpenAiModelsNewestFirstAndStripsGeminiPrefix() = runTest {
        var url = ""
        val body = """{"object":"list","data":[{"id":"old","created":100},{"id":"new","created":300},{"id":"mid","created":200}]}"""
        val models = client { req ->
            url = req.url.toString()
            respond(ByteReadChannel(body), HttpStatusCode.OK, json)
        }.listModels(openai, "sk-test")
        assertEquals("https://api.openai.com/v1/models", url)
        assertEquals(listOf("new", "mid", "old"), models.map { it.id })

        val gemini = client {
            respond(ByteReadChannel("""{"data":[{"id":"models/g-pro"},{"id":"models/g-flash"}]}"""), HttpStatusCode.OK, json)
        }.listModels(ProviderEndpoint(AiProvider.GEMINI, AiProvider.GEMINI.defaultBaseUrl!!), "k")
        assertEquals(listOf("g-pro", "g-flash"), gemini.map { it.id })
    }

    @Test
    fun listsAnthropicModelsAcrossPages() = runTest {
        val urls = mutableListOf<String>()
        var version: String? = null
        val models = client { req ->
            urls += req.url.toString()
            version = req.headers["anthropic-version"]
            val page = if (req.url.parameters["after_id"] == null) {
                """{"data":[{"id":"c-2","display_name":"C Two"}],"has_more":true,"last_id":"c-2"}"""
            } else {
                """{"data":[{"id":"c-1","display_name":"C One"}],"has_more":false,"last_id":"c-1"}"""
            }
            respond(ByteReadChannel(page), HttpStatusCode.OK, json)
        }.listModels(ProviderEndpoint(AiProvider.ANTHROPIC, "https://api.anthropic.com/v1"), "sk-ant")
        assertEquals(listOf("c-2", "c-1"), models.map { it.id })
        assertEquals("C Two", models[0].displayName)
        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("https://api.anthropic.com/v1/models"))
        assertEquals(AnthropicApi.API_VERSION, version)
    }

    @Test
    fun listWithBadKeyIsUnauthorized() = runTest {
        assertFailsWith<VisionException.Unauthorized> {
            client { respond(ByteReadChannel("""{"error":{"code":"invalid_api_key"}}"""), HttpStatusCode.Unauthorized, json) }
                .listModels(openai, "bad")
        }
    }

    @Test
    fun visionCheck() = runTest {
        var sentImage = ""
        val yes = client { req ->
            sentImage = req.body.toByteArray().decodeToString()
            respond(ByteReadChannel(openAiSse("Red.")), HttpStatusCode.OK, sse)
        }.modelFor(openai, "m").checkVision("k")
        assertTrue(yes)
        assertTrue("data:image/png;base64," in sentImage)

        assertFalse(client { respond(ByteReadChannel(openAiSse("Blue")), HttpStatusCode.OK, sse) }.modelFor(openai, "m").checkVision("k"))
        // 文字模型收到图片会 400：算不能看图。
        assertFalse(
            client { respond(ByteReadChannel("""{"error":{"code":"invalid_value"}}"""), HttpStatusCode.BadRequest, json) }
                .modelFor(openai, "m").checkVision("k"),
        )
        // Key 错不是“不能看图”，要往外抛。
        assertFailsWith<VisionException.Unauthorized> {
            client { respond(ByteReadChannel("{}"), HttpStatusCode.Unauthorized, json) }.modelFor(openai, "m").checkVision("k")
        }
    }

    @Test
    fun claudeVisionCheckSendsPng() = runTest {
        var body = ""
        val ok = client { req ->
            body = req.body.toByteArray().decodeToString()
            val stream = "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Red\"}}\n\n" +
                "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
            respond(ByteReadChannel(stream), HttpStatusCode.OK, sse)
        }.modelFor(ProviderEndpoint(AiProvider.ANTHROPIC, "https://api.anthropic.com/v1"), "c").checkVision("k")
        assertTrue(ok)
        assertTrue("\"media_type\":\"image/png\"" in body)
    }
}
