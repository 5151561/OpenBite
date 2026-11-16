package com.openbite.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptReadingTest {
    @Test
    fun parsesJsonInsideFencesAndProse() {
        val receipt = parseReceipt(
            "Here you go:\n```json\n{\"placeName\":\" とり松 \",\"items\":[{\"name\":\"生ビール\",\"qty\":0,\"amountMinor\":1100},{\"name\":\" \"}]," +
                "\"subtotalMinor\":3860,\"taxMinor\":386,\"totalMinor\":4246,\"currency\":\"jpy\"}\n```",
        )!!
        assertEquals("とり松", receipt.placeName)
        assertEquals("JPY", receipt.currency)
        assertEquals(4246L, receipt.totalMinor)
        assertEquals(1, receipt.items.single().qty)
    }

    @Test
    fun garbageIsNull() {
        assertNull(parseReceipt("no json here"))
        assertNull(parseReceipt("{broken"))
    }

    @Test
    fun sampleReceiptMatchesDesign() = runTest {
        val receipt = SampleVisionModel().readReceipt(ReceiptReadRequest(byteArrayOf(), "zh-CN", null))
        assertEquals(4, receipt.items.size)
        assertEquals(3860L, receipt.subtotalMinor)
        assertEquals(386L, receipt.taxMinor)
        assertEquals(4246L, receipt.totalMinor)
    }

    private fun sse(text: String): String {
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        return "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"$escaped\"}}\n\n" +
            "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"
    }

    private fun model(status: HttpStatusCode, body: String, onBody: (String) -> Unit = {}) = ClaudeVisionModel(
        createVisionHttpClient(
            MockEngine { req ->
                onBody(req.body.toByteArray().decodeToString())
                respond(ByteReadChannel(body), status, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            },
        ),
        AnthropicModels.HAIKU,
    )

    @Test
    fun claudeReadsReceiptFromStream() = runTest {
        var sent = ""
        val receipt = model(HttpStatusCode.OK, sse("{\"totalMinor\":4246,\"currency\":\"JPY\"}")) { sent = it }
            .readReceipt(ReceiptReadRequest(byteArrayOf(1), "zh-CN", "sk-ant-x"))
        assertEquals(4246L, receipt.totalMinor)
        assertTrue("receipt" in sent)
    }

    @Test
    fun claudeUnreadableOrUnauthorized() = runTest {
        assertFailsWith<VisionException.StreamError> {
            model(HttpStatusCode.OK, sse("sorry")).readReceipt(ReceiptReadRequest(byteArrayOf(1), "zh-CN", "sk-ant-x"))
        }
        assertFailsWith<VisionException.Unauthorized> {
            model(HttpStatusCode.Unauthorized, "{}").readReceipt(ReceiptReadRequest(byteArrayOf(1), "zh-CN", "sk-ant-x"))
        }
    }
}
