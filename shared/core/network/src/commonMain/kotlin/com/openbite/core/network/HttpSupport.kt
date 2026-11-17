package com.openbite.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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

/** 超时、断网这类底层异常统一翻译成 [VisionException]；取消照常往外抛。 */
internal suspend fun <T> mapTransportErrors(block: suspend () -> T): T =
    try {
        block()
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

internal fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

internal fun JsonObject.long(key: String): Long? = runCatching { this[key]?.jsonPrimitive?.contentOrNull?.toLong() }.getOrNull()
