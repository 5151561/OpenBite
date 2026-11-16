package com.openbite.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * 视觉模型的统一接口。换模型只换实现，不改调用方。
 * 图片在传进来之前已经去掉 EXIF、压缩到长边 1568px。
 */
interface VisionModel {
    /** 模型标识，写进 menu_scan.model，如 Anthropic 的模型 id，示例菜单是 "sample"。 */
    val id: String

    /**
     * 流式识别。先发一条 [ScanEvent.Header]（如果模型给了），每认出一道菜发一条 [ScanEvent.DishScanned]，
     * 最后发 [ScanEvent.Finished]，带上模型的原始输出。出错时抛 [VisionException]。
     */
    fun scanMenu(request: MenuScanRequest): Flow<ScanEvent>

    /** 读店家的纸质小票：明细、小计、税、合计、币种。出错抛 [VisionException]。 */
    suspend fun readReceipt(request: ReceiptReadRequest): ScannedReceipt
}

class ReceiptReadRequest(
    val jpegBytes: ByteArray,
    /** 明细译名用的语言。 */
    val targetLang: String,
    val apiKey: String?,
)

/** 小票上的一行。[amountMinor] 是这一行的金额（单价 × 数量）。 */
@Serializable
data class ReceiptLineItem(
    val name: String,
    val nameTranslated: String? = null,
    val qty: Int = 1,
    val amountMinor: Long? = null,
)

/** 模型读出来的小票。金额都是最小货币单位。 */
@Serializable
data class ScannedReceipt(
    val placeName: String? = null,
    val items: List<ReceiptLineItem> = emptyList(),
    val subtotalMinor: Long? = null,
    val taxMinor: Long? = null,
    val totalMinor: Long? = null,
    val currency: String? = null,
)

class MenuScanRequest(
    val jpegBytes: ByteArray,
    /** 目标语言，BCP 47，如 "zh-CN"。 */
    val targetLang: String,
    /** 示例菜单不需要。 */
    val apiKey: String?,
)

/** 模型输出的一道菜，字段与 Prompt 里约定的 JSON 结构一一对应。 */
@Serializable
data class ScannedDish(
    val category: String = "",
    val nameOriginal: String,
    val nameTranslated: String = "",
    /** 最小货币单位：980 日元 = 980，12.50 美元 = 1250。 */
    val priceMinor: Long? = null,
    val currency: String? = null,
    /** 模型推测，界面必须标“仅供参考”。 */
    val allergens: List<String> = emptyList(),
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
    class Timeout(cause: Throwable?) : VisionException("timeout", cause)
    class Network(cause: Throwable?) : VisionException("network", cause)

    /** 流里的 error 事件（如 overloaded_error）。 */
    class StreamError(val type: String, detail: String?) : VisionException("$type ${detail.orEmpty()}")
}

/** HTTP 状态码 -> 异常。200 返回 null。 */
fun visionExceptionFor(status: Int, detail: String? = null): VisionException? = when {
    status in 200..299 -> null
    status == 401 || status == 403 -> VisionException.Unauthorized(status)
    status == 429 -> VisionException.RateLimited()
    status == 529 || status >= 500 -> VisionException.Unavailable(status)
    else -> VisionException.BadRequest(status, detail)
}
