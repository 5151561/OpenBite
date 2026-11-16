package com.openbite.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * 视觉模型的统一接口。换模型只换实现，不改调用方。
 * 图片在传进来之前已经去掉 EXIF、压缩到长边 1568px。
 */
interface VisionModel {
    /** 模型标识，写进 menu_scan.model，如 "claude-haiku-5.5"。 */
    val id: String

    /** 流式识别：每识别出一道菜就发一条，界面逐条渲染。 */
    fun scanMenu(request: MenuScanRequest): Flow<ScannedDish>
}

class MenuScanRequest(
    val jpegBytes: ByteArray,
    /** 目标语言，BCP 47，如 "zh-CN"。 */
    val targetLang: String,
    val apiKey: String,
)

/** 模型输出的一道菜，字段与 Prompt 里约定的 JSON 结构一一对应。 */
@Serializable
data class ScannedDish(
    val category: String,
    val nameOriginal: String,
    val nameTranslated: String,
    val priceMinor: Long? = null,
    val currency: String? = null,
    val allergens: List<String> = emptyList(),
)
