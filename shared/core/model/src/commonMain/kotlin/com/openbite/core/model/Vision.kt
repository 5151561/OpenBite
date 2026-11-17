package com.openbite.core.model

import kotlinx.serialization.Serializable

// 视觉模型读出来的结果。网络层解析成这些类型，ViewModel 和界面直接用。

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
