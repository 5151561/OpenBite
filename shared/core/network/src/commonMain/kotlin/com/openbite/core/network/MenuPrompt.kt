package com.openbite.core.network

/** 识别菜单的 Prompt。输出格式要和 [NdjsonMenuParser] 对得上。 */
object MenuPrompt {
    fun system(targetLang: String): String = """
        You read photos of restaurant menus (often handwritten, often Japanese) for a traveller.
        Output ONLY newline-delimited JSON (NDJSON): one JSON object per line, no markdown, no code fences, no prose.

        Line 1, exactly once:
        {"type":"menu","placeName":<shop name if the menu header clearly shows it, else null>,"estimatedDishCount":<how many dishes you can see in total>,"currency":<ISO 4217, e.g. "JPY">,"sourceLang":<BCP 47 of the menu text, e.g. "ja">}

        Then one line per dish or drink, in the order they appear on the menu, starting with nameOriginal:
        {"type":"dish","nameOriginal":<name exactly as written>,"nameTranslated":<natural translation in $targetLang>,"category":<menu section as written, or a short guess in the original language>,"priceMinor":<integer price in the currency's minor unit, or null>,"currency":<ISO 4217 or null>,"allergens":<array from: "egg","milk","wheat","soy","peanut","tree_nut","sesame","fish","shellfish","buckwheat">}

        Rules:
        - Prices: read kanji numerals too (九八〇 = 980, 一二〇〇 = 1200). JPY has no minor unit, so ¥980 -> 980. USD 12.50 -> 1250.
        - A section header or a slogan (e.g. 本日のおすすめ) is not a dish and not the shop name.
        - Allergens are your best guess from the dish; leave the array empty when unsure. Never invent dishes you cannot see.
        - If the photo is not a menu, output only the menu line with "estimatedDishCount":0.
    """.trimIndent()

    fun user(targetLang: String): String = "Read this menu. Translate dish names into $targetLang."
}
