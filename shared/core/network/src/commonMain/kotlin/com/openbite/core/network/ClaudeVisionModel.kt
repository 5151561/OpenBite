package com.openbite.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Anthropic 的模型 id 都放在这里，别处不要写死。
 * 注意：Claude 5.5 的正式 API id 需要对照 Anthropic 文档确认。
 */
object AnthropicModels {
    const val HAIKU = "claude-haiku-5-5"
    const val SONNET = "claude-sonnet-5-5"

    const val API_URL = "https://api.anthropic.com/v1/messages"
    const val API_VERSION = "2023-06-01"
}

/** 识别用的 HTTP 客户端：不自动抛状态码异常（我们自己映射），带超时。引擎由平台给。 */
fun createVisionHttpClient(engine: HttpClientEngine, extra: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            // 两段 SSE 之间最多等 60 秒；整次识别最多 3 分钟。
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 180_000
        }
        extra()
    }

/** Claude 视觉识别：Messages API + stream=true，图片 base64 JPEG，输出 NDJSON。 */
class ClaudeVisionModel(
    private val client: HttpClient,
    override val id: String,
    private val maxTokens: Int = 4096,
    private val url: String = AnthropicModels.API_URL,
) : VisionModel {

    @OptIn(ExperimentalEncodingApi::class)
    override fun scanMenu(request: MenuScanRequest): Flow<ScanEvent> = flow {
        val apiKey = request.apiKey?.takeIf { it.isNotBlank() } ?: throw VisionException.Unauthorized(401)
        val body = buildJsonObject {
            put("model", id)
            put("max_tokens", maxTokens)
            put("stream", true)
            put("system", MenuPrompt.system(request.targetLang))
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", Base64.encode(request.jpegBytes))
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", MenuPrompt.user(request.targetLang))
                        }
                    }
                }
            }
        }.toString()

        val parser = NdjsonMenuParser()
        try {
            client.preparePost(url) {
                header("x-api-key", apiKey)
                header("anthropic-version", AnthropicModels.API_VERSION)
                header("accept", "text/event-stream")
                setBody(TextContent(body, ContentType.Application.Json))
            }.execute { response ->
                val status = response.status.value
                if (status !in 200..299) {
                    // 400 类错误只取 Anthropic 的错误类型，不留响应正文。
                    val detail = if (status in 400..499) errorType(response.bodyAsText()) else null
                    visionExceptionFor(status, detail)?.let { throw it }
                }
                val channel = response.bodyAsChannel()
                val sse = SseParser()
                while (true) {
                    val line = channel.readLine() ?: break
                    val event = sse.feedLine(line) ?: continue
                    if (handle(event, parser)) return@execute
                }
                sse.flush()?.let { handle(it, parser) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: VisionException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw VisionException.Timeout(e)
        } catch (e: ConnectTimeoutException) {
            throw VisionException.Timeout(e)
        } catch (e: SocketTimeoutException) {
            throw VisionException.Timeout(e)
        } catch (e: Exception) {
            throw VisionException.Network(e)
        }
        parser.finish().forEach { emit(it) }
        emit(ScanEvent.Finished(parser.rawOutput))
    }

    /** 处理一条 SSE；返回 true 表示流结束。 */
    private suspend fun FlowCollector<ScanEvent>.handle(event: SseEvent, parser: NdjsonMenuParser): Boolean =
        when (val part = parseAnthropicEvent(event)) {
            is AnthropicStreamPart.TextDelta -> {
                parser.feed(part.text).forEach { emit(it) }
                false
            }
            is AnthropicStreamPart.Error -> throw when (part.type) {
                "overloaded_error", "api_error" -> VisionException.Unavailable(529)
                "rate_limit_error" -> VisionException.RateLimited()
                "authentication_error", "permission_error" -> VisionException.Unauthorized(401)
                else -> VisionException.StreamError(part.type, part.message)
            }
            AnthropicStreamPart.Stop -> true
            null -> false
        }

    private fun errorType(body: String): String? = runCatching {
        parseAnthropicEvent(SseEvent("error", body)).let { (it as? AnthropicStreamPart.Error)?.type }
    }.getOrNull()
}
