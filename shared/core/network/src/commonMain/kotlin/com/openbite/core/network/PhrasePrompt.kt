package com.openbite.core.network

/**
 * 把小票的英文句子表翻成别的语言。输入、输出都是同样键的 JSON 对象，由领域层校验。
 * 不写死模型：用用户在设置里选的那个。
 */
object PhrasePrompt {
    fun system(languageName: String, languageTag: String): String = """
        You translate short phrases that a traveller shows to restaurant staff. Target language: $languageName ($languageTag).
        Output ONLY one JSON object with exactly the same keys and the same array lengths as the input. No markdown, no prose.

        Rules:
        - Polite, natural phrasing a local would use with a waiter. Keep each phrase short.
        - "allergens", "avoids": plain noun phrases, keep the order.
        - "spice": four short polite requests, from "not spicy" to "extra spicy", keep the order.
        - "allergyTemplate", "avoidTemplate": full sentences that contain the placeholder {items} exactly once, unchanged.
        - "listSeparator": what goes between list items (for example ", "); "andWord" / "orWord": what joins the last two items, with spaces if the language uses them (for example " and ").
        - "serialComma": true only if the language puts a comma before the last "and" in a list of three.
    """.trimIndent()

    fun user(englishJson: String): String = "Translate this JSON:\n$englishJson"
}
