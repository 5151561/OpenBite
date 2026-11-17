package com.openbite.core.network

import com.openbite.core.model.ProviderModel
import com.openbite.core.model.ScannedReceipt
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * OpenAI Chat Completions 格式（stream=true，图片用 data: URL）。OpenAI、Gemini 的兼容接口、DeepSeek 和自定义地址都走这个。
 * 不传 max_tokens：各家上限不一样，传大了会 400，推理模型传小了又会被思考占满；用服务商默认值。
 */
class OpenAiCompatVisionModel(
    private val client: HttpClient,
    override val id: String,
    /** 如 https://api.openai.com/v1，结尾不带 /。 */
    private val baseUrl: String,
) : VisionModel {

    override fun scanMenu(request: MenuScanRequest): Flow<ScanEvent> = flow {
        val parser = NdjsonMenuParser()
        streamText(jpegUrl(request.jpegBytes), request.apiKey, MenuPrompt.system(request.targetLang), MenuPrompt.user(request.targetLang)) { text ->
            parser.feed(text).forEach { emit(it) }
        }
        parser.finish().forEach { emit(it) }
        emit(ScanEvent.Finished(parser.rawOutput))
    }

    override suspend fun readReceipt(request: ReceiptReadRequest): ScannedReceipt {
        val text = StringBuilder()
        streamText(jpegUrl(request.jpegBytes), request.apiKey, ReceiptPrompt.system(request.targetLang), ReceiptPrompt.USER) { text.append(it) }
        return parseReceipt(text.toString()) ?: throw VisionException.StreamError("unreadable_receipt", null)
    }

    override suspend fun checkVision(apiKey: String): Boolean = VisionProbe.run { onText ->
        streamText("data:${VisionProbe.MEDIA_TYPE};base64,${VisionProbe.PNG_BASE64}", apiKey, VisionProbe.SYSTEM, VisionProbe.USER, onText)
    }

    override suspend fun completeText(apiKey: String?, system: String, user: String): String {
        val text = StringBuilder()
        streamText(null, apiKey, system, user) { text.append(it) }
        return text.toString()
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun jpegUrl(bytes: ByteArray): String = "data:image/jpeg;base64," + Base64.encode(bytes)

    /** [imageUrl] 为 null 时只发文字。 */
    private suspend fun streamText(
        imageUrl: String?,
        apiKeyOrNull: String?,
        system: String,
        user: String,
        onText: suspend (String) -> Unit,
    ) {
        val apiKey = apiKeyOrNull?.takeIf { it.isNotBlank() } ?: throw VisionException.Unauthorized(401)
        val body = buildJsonObject {
            put("model", id)
            put("stream", true)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    if (imageUrl == null) {
                        put("content", user)
                    } else {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", imageUrl) }
                            }
                            addJsonObject {
                                put("type", "text")
                                put("text", user)
                            }
                        }
                    }
                }
            }
        }.toString()

        mapTransportErrors {
            client.preparePost("$baseUrl/chat/completions") {
                bearerAuth(apiKey)
                header("accept", "text/event-stream")
                setBody(TextContent(body, ContentType.Application.Json))
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    val detail = if (status in 400..499) openAiErrorCode(response.bodyAsText()) else null
                    visionExceptionFor(status, detail)?.let { throw it }
                }
                val channel = response.bodyAsChannel()
                val sse = SseParser()
                var finished = false
                while (true) {
                    val line = channel.readLine() ?: break
                    val event = sse.feedLine(line) ?: continue
                    when (val part = parseOpenAiChunk(event.data)) {
                        is OpenAiStreamPart.TextDelta -> onText(part.text)
                        is OpenAiStreamPart.Finish -> {
                            if (part.reason == "length") throw VisionException.Truncated("max_tokens")
                            finished = true
                        }
                        is OpenAiStreamPart.Error -> throw part.toException()
                        OpenAiStreamPart.Done -> return@execute
                        null -> Unit
                    }
                }
                when (val last = sse.flush()?.let { parseOpenAiChunk(it.data) }) {
                    is OpenAiStreamPart.TextDelta -> onText(last.text)
                    is OpenAiStreamPart.Finish -> if (last.reason == "length") throw VisionException.Truncated("max_tokens") else finished = true
                    is OpenAiStreamPart.Error -> throw last.toException()
                    OpenAiStreamPart.Done -> finished = true
                    null -> Unit
                }
                // 有的兼容接口不发 [DONE]，有 finish_reason 就算写完了；两样都没有是半路断了。
                if (!finished) throw VisionException.Truncated("eof")
            }
        }
    }
}

/** OpenAI 流里我们关心的部分。 */
sealed interface OpenAiStreamPart {
    data class TextDelta(val text: String) : OpenAiStreamPart

    /** choices[0].finish_reason：stop、length、content_filter…… */
    data class Finish(val reason: String) : OpenAiStreamPart
    data class Error(val code: String?) : OpenAiStreamPart
    data object Done : OpenAiStreamPart
}

private fun OpenAiStreamPart.Error.toException(): VisionException = when (code) {
    "rate_limit_exceeded", "insufficient_quota" -> VisionException.RateLimited()
    "invalid_api_key" -> VisionException.Unauthorized(401)
    "model_not_found" -> VisionException.ModelNotFound(code)
    "server_error", "overloaded" -> VisionException.Unavailable(503)
    else -> VisionException.StreamError(code ?: "error", null)
}

/** 一条 SSE 的 data。思考过程（reasoning_content）不要，只要正文。 */
fun parseOpenAiChunk(data: String): OpenAiStreamPart? {
    if (data.trim() == "[DONE]") return OpenAiStreamPart.Done
    val obj = runCatching { lenientJson.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
    obj["error"]?.let { return OpenAiStreamPart.Error(errorCodeOf(it)) }
    val choice = (obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
    val text = (choice["delta"] as? JsonObject)?.string("content")
    if (!text.isNullOrEmpty()) return OpenAiStreamPart.TextDelta(text)
    return choice.string("finish_reason")?.let { OpenAiStreamPart.Finish(it) }
}

/**
 * 错误正文里的代码，只取 code / type / status，不留 message（可能回显请求内容）。
 * OpenAI 是 {"error":{...}}，Gemini 的兼容接口有时是 [{"error":{...}}]。
 */
internal fun openAiErrorCode(body: String): String? = runCatching {
    val root = lenientJson.parseToJsonElement(body)
    val obj = (root as? JsonArray)?.firstOrNull()?.jsonObject ?: root.jsonObject
    obj["error"]?.let(::errorCodeOf)
}.getOrNull()

private fun errorCodeOf(error: JsonElement): String? {
    val obj = error as? JsonObject ?: return null
    return obj.string("code")?.takeIf { it.any(Char::isLetter) } ?: obj.string("type") ?: obj.string("status")
}

/** GET {base}/models。有 created 的按新到旧排；Gemini 的 id 去掉 “models/” 前缀。出错抛 [VisionException]。 */
internal suspend fun listOpenAiModels(
    client: HttpClient,
    baseUrl: String,
    apiKey: String,
    stripPrefix: String? = null,
): List<ProviderModel> = mapTransportErrors {
    val response = client.get("$baseUrl/models") { bearerAuth(apiKey) }
    val text = response.bodyAsText()
    visionExceptionFor(response.status.value, openAiErrorCode(text))?.let { throw it }
    val data = runCatching { lenientJson.parseToJsonElement(text).jsonObject["data"]?.jsonArray }.getOrNull()
        ?: throw VisionException.BadRequest(response.status.value, "unreadable_models")
    data.mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val raw = obj.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val id = if (stripPrefix != null) raw.removePrefix(stripPrefix) else raw
        ProviderModel(id, obj.string("display_name"), obj.long("created"))
    }.distinctBy { it.id }
        .sortedByDescending { it.createdAtSeconds ?: Long.MIN_VALUE }
}
