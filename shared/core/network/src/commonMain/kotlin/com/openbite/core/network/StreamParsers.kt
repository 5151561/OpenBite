package com.openbite.core.network

import com.openbite.core.model.ScannedDish
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** 一条 Server-Sent Event。 */
data class SseEvent(val event: String?, val data: String)

/**
 * 按行读 SSE（text/event-stream）：`event:` 和 `data:` 累积，遇到空行发出一条事件。
 * 多行 data 按规范用换行拼起来；`:` 开头的注释行忽略。
 */
class SseParser {
    private var event: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** 喂一行（不含换行符）。返回这一行结束的事件，没有就返回 null。 */
    fun feedLine(line: String): SseEvent? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return null
        val field = line.substringBefore(':')
        val value = line.substringAfter(':', missingDelimiterValue = "").removePrefix(" ")
        when (field) {
            "event" -> event = value
            "data" -> {
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
        }
        return null
    }

    /** 流结束时把最后一条没有空行收尾的事件也发出来。 */
    fun flush(): SseEvent? {
        val result = if (hasData) SseEvent(event, data.toString()) else null
        event = null
        data.clear()
        hasData = false
        return result
    }
}

/** Anthropic 流里我们关心的部分。 */
sealed interface AnthropicStreamPart {
    data class TextDelta(val text: String) : AnthropicStreamPart
    data class Error(val type: String, val message: String?) : AnthropicStreamPart
    data object Stop : AnthropicStreamPart

    /** message_delta 里带的 stop_reason（end_turn、max_tokens…）。 */
    data class StopReason(val reason: String) : AnthropicStreamPart
}

/** 把一条 SSE 事件翻译成文本增量 / 错误 / 结束；ping、message_start 等返回 null。 */
fun parseAnthropicEvent(event: SseEvent): AnthropicStreamPart? {
    val obj = runCatching { lenientJson.parseToJsonElement(event.data).jsonObject }.getOrNull() ?: return null
    val type = obj.string("type") ?: event.event
    return when (type) {
        "content_block_delta" -> {
            val delta = obj["delta"]?.jsonObject ?: return null
            if (delta.string("type") == "text_delta") delta.string("text")?.let { AnthropicStreamPart.TextDelta(it) } else null
        }
        "error" -> {
            val error = obj["error"]?.jsonObject
            AnthropicStreamPart.Error(error?.string("type") ?: "error", error?.string("message"))
        }
        "message_delta" -> obj["delta"]?.jsonObject?.string("stop_reason")?.let { AnthropicStreamPart.StopReason(it) }
        "message_stop" -> AnthropicStreamPart.Stop
        else -> null
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

/**
 * 模型按行输出 JSON（NDJSON），一行一个对象：
 * - `{"type":"menu","placeName":…,"estimatedDishCount":…,"currency":…,"sourceLang":…}`
 * - `{"type":"dish","nameOriginal":…,"nameTranslated":…,…}`
 *
 * 文本增量可能在任意位置断开，这里攒到换行再解析。解析不了的行（比如模型多写的 ``` 或说明文字）跳过。
 * 一行还没写完时，如果已经能看到 `"nameOriginal":"…"`，先发 [ScanEvent.DishStarted]。
 */
class NdjsonMenuParser {
    private val pending = StringBuilder()
    private val raw = StringBuilder()
    private var startedForCurrentLine = false

    /** 原始输出（全部文本增量拼起来）。 */
    val rawOutput: String get() = raw.toString()

    fun feed(text: String): List<ScanEvent> {
        raw.append(text)
        val events = mutableListOf<ScanEvent>()
        for (ch in text) {
            if (ch == '\n') {
                parseLine(pending.toString())?.let(events::add)
                pending.clear()
                startedForCurrentLine = false
            } else {
                pending.append(ch)
            }
        }
        if (!startedForCurrentLine) {
            partialDishName(pending)?.let {
                startedForCurrentLine = true
                events += ScanEvent.DishStarted(it)
            }
        }
        return events
    }

    /** 流结束：最后一行可能没有换行。 */
    fun finish(): List<ScanEvent> {
        val last = parseLine(pending.toString())
        pending.clear()
        return listOfNotNull(last)
    }

    private fun parseLine(line: String): ScanEvent? {
        val trimmed = line.trim().removeSuffix(",")
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
        val obj = runCatching { lenientJson.parseToJsonElement(trimmed).jsonObject }.getOrNull() ?: return null
        return when (obj.string("type")) {
            "menu" -> ScanEvent.Header(
                placeName = obj.string("placeName")?.trim()?.takeIf { it.isNotEmpty() },
                estimatedDishCount = obj["estimatedDishCount"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
                currency = obj.string("currency")?.uppercase()?.takeIf { it.length == 3 },
                sourceLang = obj.string("sourceLang"),
            )
            "dish", null -> runCatching { lenientJson.decodeFromJsonElement<ScannedDish>(obj) }.getOrNull()
                ?.takeIf { it.nameOriginal.isNotBlank() }
                ?.let { ScanEvent.DishScanned(it.copy(nameOriginal = it.nameOriginal.trim(), nameTranslated = it.nameTranslated.trim())) }
            else -> null
        }
    }

    private companion object {
        val partialName = Regex("\"nameOriginal\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"")

        fun partialDishName(line: CharSequence): String? =
            partialName.find(line)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
    }
}
