package com.openbite.core.network

import com.openbite.core.model.ProviderModel
import com.openbite.core.model.ScannedReceipt
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Anthropic 接口的固定部分。不写模型名：模型从 /v1/models 拉。 */
object AnthropicApi {
    const val API_VERSION = "2023-06-01"
}

/** Claude 视觉识别：Messages API + stream=true，图片 base64。菜单输出 NDJSON，小票输出一个 JSON 对象。 */
class ClaudeVisionModel(
    private val client: HttpClient,
    override val id: String,
    /** 如 https://api.anthropic.com/v1，结尾不带 /。 */
    private val baseUrl: String,
    private val maxTokens: Int = 4096,
) : VisionModel {

    override fun scanMenu(request: MenuScanRequest): Flow<ScanEvent> = flow {
        val parser = NdjsonMenuParser()
        streamText(jpeg(request.jpegBytes), JPEG, request.apiKey, MenuPrompt.system(request.targetLang), MenuPrompt.user(request.targetLang), maxTokens) { text ->
            parser.feed(text).forEach { emit(it) }
        }
        parser.finish().forEach { emit(it) }
        emit(ScanEvent.Finished(parser.rawOutput))
    }

    override suspend fun readReceipt(request: ReceiptReadRequest): ScannedReceipt {
        val text = StringBuilder()
        streamText(jpeg(request.jpegBytes), JPEG, request.apiKey, ReceiptPrompt.system(request.targetLang), ReceiptPrompt.USER, maxTokens) { text.append(it) }
        return parseReceipt(text.toString()) ?: throw VisionException.StreamError("unreadable_receipt", null)
    }

    override suspend fun checkVision(apiKey: String): Boolean = VisionProbe.run { onText ->
        streamText(VisionProbe.PNG_BASE64, VisionProbe.MEDIA_TYPE, apiKey, VisionProbe.SYSTEM, VisionProbe.USER, 64, onText)
    }

    override suspend fun completeText(apiKey: String?, system: String, user: String): String {
        val text = StringBuilder()
        streamText(null, JPEG, apiKey, system, user, maxTokens) { text.append(it) }
        return text.toString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun jpeg(bytes: ByteArray): String = Base64.encode(bytes)

    /** 发一张图（[imageBase64] 为 null 时只发文字）和两段提示词，流式收文本增量。出错抛 [VisionException]。 */
    private suspend fun streamText(
        imageBase64: String?,
        mediaType: String,
        apiKeyOrNull: String?,
        system: String,
        user: String,
        maxTokens: Int,
        onText: suspend (String) -> Unit,
    ) {
        val apiKey = apiKeyOrNull?.takeIf { it.isNotBlank() } ?: throw VisionException.Unauthorized(401)
        val body = buildJsonObject {
            put("model", id)
            put("max_tokens", maxTokens)
            put("stream", true)
            put("system", system)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        if (imageBase64 != null) {
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", mediaType)
                                    put("data", imageBase64)
                                }
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", user)
                        }
                    }
                }
            }
        }.toString()

        mapTransportErrors {
            client.preparePost("$baseUrl/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", AnthropicApi.API_VERSION)
                header("accept", "text/event-stream")
                setBody(TextContent(body, ContentType.Application.Json))
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    // 400 类错误只取 Anthropic 的错误类型，不留响应正文。
                    val detail = if (status in 400..499) anthropicErrorType(response.bodyAsText()) else null
                    visionExceptionFor(status, detail)?.let { throw it }
                }
                val channel = response.bodyAsChannel()
                val sse = SseParser()
                val stream = StreamState()
                while (true) {
                    val line = channel.readLine() ?: break
                    val event = sse.feedLine(line) ?: continue
                    if (handle(event, stream, onText)) return@execute
                }
                if (sse.flush()?.let { handle(it, stream, onText) } == true) return@execute
                // 没等到 message_stop 连接就断了（代理截断、网络中途断开）：结果不完整。
                throw VisionException.Truncated("eof")
            }
        }
    }

    private class StreamState {
        var stopReason: String? = null
    }

    /** 处理一条 SSE；返回 true 表示流正常结束。写到 max_tokens 被截断时抛 [VisionException.Truncated]。 */
    private suspend fun handle(event: SseEvent, stream: StreamState, onText: suspend (String) -> Unit): Boolean =
        when (val part = parseAnthropicEvent(event)) {
            is AnthropicStreamPart.TextDelta -> {
                onText(part.text)
                false
            }
            is AnthropicStreamPart.StopReason -> {
                stream.stopReason = part.reason
                false
            }
            is AnthropicStreamPart.Error -> throw when (part.type) {
                "overloaded_error", "api_error" -> VisionException.Unavailable(529)
                "rate_limit_error" -> VisionException.RateLimited()
                "authentication_error", "permission_error" -> VisionException.Unauthorized(401)
                "not_found_error" -> VisionException.ModelNotFound(part.type)
                else -> VisionException.StreamError(part.type, part.message)
            }
            AnthropicStreamPart.Stop -> {
                if (stream.stopReason == "max_tokens") throw VisionException.Truncated("max_tokens")
                true
            }
            null -> false
        }

    private companion object {
        const val JPEG = "image/jpeg"
    }
}

internal fun anthropicErrorType(body: String): String? = runCatching {
    parseAnthropicEvent(SseEvent("error", body)).let { (it as? AnthropicStreamPart.Error)?.type }
}.getOrNull()

/**
 * GET /v1/models，按服务商给的顺序（新的在前），分页拉完（最多 10 页）。出错抛 [VisionException]。
 */
internal suspend fun listAnthropicModels(client: HttpClient, baseUrl: String, apiKey: String): List<ProviderModel> = mapTransportErrors {
    val models = mutableListOf<ProviderModel>()
    var after: String? = null
    repeat(10) {
        val response = client.get("$baseUrl/models") {
            header("x-api-key", apiKey)
            header("anthropic-version", AnthropicApi.API_VERSION)
            parameter("limit", 1000)
            after?.let { parameter("after_id", it) }
        }
        val text = response.bodyAsText()
        visionExceptionFor(response.status.value, anthropicErrorType(text))?.let { throw it }
        val obj = runCatching { lenientJson.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw VisionException.BadRequest(response.status.value, "unreadable_models")
        obj["data"]?.jsonArray?.forEach { item ->
            val model = item as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val id = model.string("id")?.takeIf { it.isNotBlank() } ?: return@forEach
            models += ProviderModel(id, model.string("display_name"))
        }
        if (obj.string("has_more") != "true") return@mapTransportErrors models
        after = obj.string("last_id") ?: return@mapTransportErrors models
    }
    models
}
