package com.openbite.core.network

import com.openbite.core.model.ScannedDish
import com.openbite.core.model.ScannedReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * 视觉模型的统一接口。换模型只换实现，不改调用方。
 * 图片在传进来之前已经去掉 EXIF、压缩到长边 1568px。
 */
interface VisionModel {
    /** 模型标识，写进 menu_scan.model：服务商返回的模型 id，演示模式是 "sample"。 */
    val id: String

    /**
     * 流式识别。先发一条 [ScanEvent.Header]（如果模型给了），每认出一道菜发一条 [ScanEvent.DishScanned]，
     * 最后发 [ScanEvent.Finished]，带上模型的原始输出。出错时抛 [VisionException]。
     */
    fun scanMenu(request: MenuScanRequest): Flow<ScanEvent>

    /** 读店家的纸质小票：明细、小计、税、合计、币种。出错抛 [VisionException]。 */
    suspend fun readReceipt(request: ReceiptReadRequest): ScannedReceipt

    /**
     * 能不能看图：发一张纯红的小图问颜色，答出 red 就算能。
     * 服务商因为“不支持图片 / 不是聊天模型 / 没这个模型”拒绝时返回 false；Key 错、断网、限流照常抛 [VisionException]。
     */
    suspend fun checkVision(apiKey: String): Boolean
}

/** 试图用的图和问题。图是 64×64 的纯红 PNG（136 字节）。 */
internal object VisionProbe {
    const val PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAT0lEQVR42u3PQQkAAAgEsItw/dMZxQi+hcEKLNO+FgEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQGBywLQTQDx3uY1cQAAAABJRU5ErkJggg=="
    const val MEDIA_TYPE = "image/png"
    const val SYSTEM = "You look at images and answer with a single English word."
    const val USER = "What is the main color of this image? Answer with one word."

    fun looksRed(answer: String): Boolean = "red" in answer.lowercase()

    /** 跑一次试图：拒绝类的错误算“不能看图”，其他错误往外抛。写到长度上限也按已经写出来的字判断。 */
    suspend fun run(stream: suspend (onText: suspend (String) -> Unit) -> Unit): Boolean {
        val text = StringBuilder()
        try {
            stream { text.append(it) }
        } catch (e: VisionException.BadRequest) {
            return false
        } catch (e: VisionException.ModelNotFound) {
            return false
        } catch (e: VisionException.Truncated) {
            return looksRed(text.toString())
        } catch (e: VisionException.StreamError) {
            return false
        }
        return looksRed(text.toString())
    }
}

class ReceiptReadRequest(
    val jpegBytes: ByteArray,
    /** 明细译名用的语言。 */
    val targetLang: String,
    val apiKey: String?,
)

class MenuScanRequest(
    val jpegBytes: ByteArray,
    /** 目标语言，BCP 47，如 "zh-CN"。 */
    val targetLang: String,
    /** 示例菜单不需要。 */
    val apiKey: String?,
)

sealed interface ScanEvent {
    /** 菜单整体信息：店名（菜单抬头上有就给）、大约几道菜、币种、原文语言。 */
    data class Header(
        val placeName: String?,
        val estimatedDishCount: Int?,
        val currency: String?,
        val sourceLang: String?,
    ) : ScanEvent

    /** 一道菜的 JSON 还没写完，但原名已经出来了，界面可以先显示原名。 */
    data class DishStarted(val nameOriginal: String) : ScanEvent

    data class DishScanned(val dish: ScannedDish) : ScanEvent

    /** 识别结束。[rawOutput] 是模型的原始文本输出，存进 menu_scan.rawJson 便于排查和重放。 */
    data class Finished(val rawOutput: String) : ScanEvent
}

/** 识别失败的原因。界面层再翻译成 ScanError。 */
sealed class VisionException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unauthorized(status: Int) : VisionException("HTTP $status")
    class RateLimited : VisionException("HTTP 429")
    class Unavailable(status: Int) : VisionException("HTTP $status")
    class BadRequest(status: Int, detail: String?) : VisionException("HTTP $status ${detail.orEmpty()}")

    /** 404 或 model_not_found：这家没有（或已经下架了）这个模型。 */
    class ModelNotFound(detail: String?) : VisionException("model not found ${detail.orEmpty()}")
    class Timeout(cause: Throwable?) : VisionException("timeout", cause)
    class Network(cause: Throwable?) : VisionException("network", cause)

    /** 流里的 error 事件（如 overloaded_error）。 */
    class StreamError(val type: String, detail: String?) : VisionException("$type ${detail.orEmpty()}")

    /**
     * 输出没写完：连接在 message_stop 之前断了（[reason] = "eof"），
     * 或者模型写到 max_tokens 被截断（[reason] = "max_tokens"）。结果不完整，不能当成功保存。
     */
    class Truncated(val reason: String) : VisionException("truncated $reason")
}

/** HTTP 状态码 -> 异常。200 返回 null。 */
fun visionExceptionFor(status: Int, detail: String? = null): VisionException? = when {
    status in 200..299 -> null
    status == 401 || status == 403 -> VisionException.Unauthorized(status)
    status == 429 -> VisionException.RateLimited()
    status == 404 || detail == "not_found_error" || detail == "model_not_found" -> VisionException.ModelNotFound(detail)
    status == 529 || status >= 500 -> VisionException.Unavailable(status)
    else -> VisionException.BadRequest(status, detail)
}
