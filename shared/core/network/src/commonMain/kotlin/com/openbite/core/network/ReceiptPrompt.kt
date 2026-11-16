package com.openbite.core.network

import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** 读店家纸质小票的 Prompt。输出一个 JSON 对象，由 [parseReceipt] 解析。 */
object ReceiptPrompt {
    fun system(targetLang: String): String = """
        You read photos of restaurant paper receipts for a traveller.
        Output ONLY one JSON object, no markdown, no prose:
        {"placeName":<shop name printed on the receipt or null>,"items":[{"name":<line as printed>,"nameTranslated":<translation in $targetLang>,"qty":<integer>,"amountMinor":<line amount in minor units>}],"subtotalMinor":<subtotal (小計) or null>,"taxMinor":<tax total (消費税 etc.) or null>,"totalMinor":<the amount actually paid (合計 / total)>,"currency":<ISO 4217>}

        Rules:
        - Minor units: JPY has none (4,246 yen -> 4246); USD 12.50 -> 1250.
        - Do not list subtotal, tax, service charge, discounts or payment lines as items.
        - If a value is not printed, use null. Never invent numbers.
    """.trimIndent()

    const val USER = "Read this receipt."
}

/** 从模型输出里取第一个 `{` 到最后一个 `}` 当 JSON 解析（容忍代码块和说明文字）。解析不了返回 null。 */
fun parseReceipt(text: String): ScannedReceipt? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    val obj = runCatching { lenientJson.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
    val receipt = runCatching { lenientJson.decodeFromJsonElement<ScannedReceipt>(obj) }.getOrNull() ?: return null
    return receipt.copy(
        placeName = receipt.placeName?.trim()?.takeIf { it.isNotEmpty() },
        currency = receipt.currency?.uppercase()?.takeIf { it.length == 3 },
        items = receipt.items.filter { it.name.isNotBlank() }.map { it.copy(name = it.name.trim(), qty = it.qty.coerceAtLeast(1)) },
        totalMinor = receipt.totalMinor?.takeIf { it >= 0 },
    )
}
