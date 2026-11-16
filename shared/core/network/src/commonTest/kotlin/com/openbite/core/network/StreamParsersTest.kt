package com.openbite.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamParsersTest {
    @Test
    fun sseParserGroupsFieldsUntilBlankLine() {
        val parser = SseParser()
        val events = CLAUDE_SSE_FIXTURE.lines().mapNotNull { parser.feedLine(it) } + listOfNotNull(parser.flush())
        assertEquals("message_start", events.first().event)
        assertEquals("message_stop", events.last().event)
        assertTrue(events.none { it.data.startsWith(":") })
    }

    @Test
    fun sseParserJoinsMultilineData() {
        val parser = SseParser()
        parser.feedLine("data: a")
        parser.feedLine("data: b")
        assertEquals(SseEvent(null, "a\nb"), parser.feedLine(""))
        assertNull(parser.flush())
    }

    @Test
    fun anthropicEventsMapToTextErrorStop() {
        assertEquals(
            AnthropicStreamPart.TextDelta("hi"),
            parseAnthropicEvent(SseEvent("content_block_delta", """{"type":"content_block_delta","delta":{"type":"text_delta","text":"hi"}}""")),
        )
        assertNull(parseAnthropicEvent(SseEvent("ping", """{"type":"ping"}""")))
        assertEquals(AnthropicStreamPart.Stop, parseAnthropicEvent(SseEvent("message_stop", """{"type":"message_stop"}""")))
        val error = parseAnthropicEvent(SseEvent("error", """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
        assertEquals(AnthropicStreamPart.Error("overloaded_error", "Overloaded"), error)
    }

    @Test
    fun ndjsonParserHandlesChunksSplitAnywhere() {
        val text = SampleVisionModel.SAMPLE_OUTPUT
        // 每 3 个字符一段，中间一定会断在 JSON 和中文中间。
        val parser = NdjsonMenuParser()
        val events = text.chunked(3).flatMap { parser.feed(it) } + parser.finish()
        val header = assertIs<ScanEvent.Header>(events.first())
        assertEquals("おばんざい 小春", header.placeName)
        assertEquals(7, header.estimatedDishCount)
        val dishes = events.filterIsInstance<ScanEvent.DishScanned>().map { it.dish }
        assertEquals(7, dishes.size)
        assertEquals("湯葉刺し", dishes[1].nameOriginal)
        assertEquals(680L, dishes[1].priceMinor)
        assertEquals(listOf("soy"), dishes[1].allergens)
        assertEquals(text, parser.rawOutput)
    }

    @Test
    fun ndjsonParserAnnouncesNameBeforeLineIsComplete() {
        val parser = NdjsonMenuParser()
        assertEquals(emptyList(), parser.feed("{\"type\":\"dish\",\"nameOri"))
        val started = parser.feed("ginal\":\"鯖の塩焼き\",\"nameTr")
        assertEquals(listOf<ScanEvent>(ScanEvent.DishStarted("鯖の塩焼き")), started)
        // 同一行不重复发。
        assertEquals(emptyList(), parser.feed("anslated\":\"盐烤"))
        val done = parser.feed("青花鱼\"}\n")
        assertEquals("盐烤青花鱼", assertIs<ScanEvent.DishScanned>(done.single()).dish.nameTranslated)
    }

    @Test
    fun ndjsonParserSkipsFencesProseAndBrokenLines() {
        val parser = NdjsonMenuParser()
        val events = parser.feed(
            "```json\nHere is the menu:\n{\"type\":\"dish\",\"nameOriginal\":\"冷奴\",\"priceMinor\":380},\n{broken\n{\"type\":\"dish\",\"nameOriginal\":\"  \"}\n```\n",
        ) + parser.finish()
        val dish = assertIs<ScanEvent.DishScanned>(events.single()).dish
        assertEquals("冷奴", dish.nameOriginal)
        assertEquals("", dish.nameTranslated)
    }

    @Test
    fun ndjsonParserParsesLastLineWithoutNewline() {
        val parser = NdjsonMenuParser()
        parser.feed("{\"type\":\"menu\",\"placeName\":\"\",\"estimatedDishCount\":0}")
        val header = assertIs<ScanEvent.Header>(parser.finish().single())
        assertNull(header.placeName)
        assertNull(header.estimatedDishCount)
    }

    @Test
    fun statusCodesMapToErrors() {
        assertNull(visionExceptionFor(200))
        assertIs<VisionException.Unauthorized>(visionExceptionFor(401))
        assertIs<VisionException.Unauthorized>(visionExceptionFor(403))
        assertIs<VisionException.RateLimited>(visionExceptionFor(429))
        assertIs<VisionException.Unavailable>(visionExceptionFor(500))
        assertIs<VisionException.Unavailable>(visionExceptionFor(529))
        assertIs<VisionException.BadRequest>(visionExceptionFor(400))
    }
}
