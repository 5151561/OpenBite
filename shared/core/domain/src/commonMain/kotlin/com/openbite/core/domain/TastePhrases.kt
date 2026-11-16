package com.openbite.core.domain

import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile

/** 小票能写的语言。菜单语言不在表里时只写中文。 */
enum class PhraseLanguage { ZH, JA, EN }

/** BCP 47 -> 小票语言：“ja”“ja-JP” -> JA，“en-US” -> EN，其余（含 null）-> ZH。 */
fun phraseLanguage(tag: String?): PhraseLanguage = when (tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')) {
    "ja" -> PhraseLanguage.JA
    "en" -> PhraseLanguage.EN
    else -> PhraseLanguage.ZH
}

/** 一句话：[primary] 用菜单语言，[secondary] 是中文；菜单本身是中文时 [secondary] 为 null。 */
data class Phrase(val primary: String, val secondary: String?)

/**
 * 小票上的固定句子。不依赖模型，离线可用。
 * 中文是对照行；日文、英文给店员看。
 */
object TastePhrases {
    private val allergenNames = mapOf(
        PhraseLanguage.ZH to listOf("花生", "虾蟹", "蛋", "乳制品", "小麦", "大豆", "鱼", "坚果", "芝麻", "荞麦"),
        PhraseLanguage.JA to listOf("ピーナッツ", "えび・かに", "卵", "乳製品", "小麦", "大豆", "魚", "ナッツ", "ごま", "そば"),
        PhraseLanguage.EN to listOf("peanuts", "shellfish", "eggs", "dairy", "wheat", "soy", "fish", "tree nuts", "sesame", "buckwheat"),
    )
    private val avoidNames = mapOf(
        PhraseLanguage.ZH to listOf("葱", "香菜", "内脏", "生食", "牛肉", "猪肉"),
        PhraseLanguage.JA to listOf("ネギ", "パクチー", "ホルモン", "生もの", "牛肉", "豚肉"),
        PhraseLanguage.EN to listOf("green onion", "cilantro", "offal", "raw food", "beef", "pork"),
    )
    private val spiceSentences = mapOf(
        PhraseLanguage.ZH to listOf("不辣", "微辣", "中辣", "特辣"),
        PhraseLanguage.JA to listOf(
            "辛くしないでください",
            "辛さ控えめでお願いします",
            "普通の辛さでお願いします",
            "辛口でお願いします",
        ),
        PhraseLanguage.EN to listOf("Not spicy, please.", "Only a little spicy, please.", "Medium spicy, please.", "Extra spicy, please."),
    )
    private val headers = mapOf(
        PhraseLanguage.ZH to "我要点这些",
        PhraseLanguage.JA to "注文をお願いします",
        PhraseLanguage.EN to "I'd like to order:",
    )
    private val disclaimers = mapOf(
        PhraseLanguage.ZH to "过敏原为 AI 推测，仅供参考，请向店员确认。",
        PhraseLanguage.JA to "アレルギー表示はAIの推定です。必ず店員さんにご確認ください。",
        PhraseLanguage.EN to "Allergen notes are an AI guess. Please check with the staff.",
    )

    fun allergenName(allergen: Allergen, lang: PhraseLanguage): String = allergenNames.getValue(lang)[allergen.ordinal]

    fun avoidName(avoid: AvoidItem, lang: PhraseLanguage): String = avoidNames.getValue(lang)[avoid.ordinal]

    fun spice(level: SpiceLevel, lang: PhraseLanguage): String = spiceSentences.getValue(lang)[level.ordinal]

    fun allergySentence(allergens: List<Allergen>, lang: PhraseLanguage): String? {
        if (allergens.isEmpty()) return null
        val names = allergens.map { allergenName(it, lang) }
        return when (lang) {
            PhraseLanguage.ZH -> names.joinToString("、") + "过敏"
            PhraseLanguage.JA -> names.joinToString("・") + "アレルギーがあります"
            PhraseLanguage.EN -> "I'm allergic to ${englishList(names, "and")}."
        }
    }

    fun avoidSentence(avoids: List<AvoidItem>, lang: PhraseLanguage): String? {
        if (avoids.isEmpty()) return null
        val names = avoids.map { avoidName(it, lang) }
        return when (lang) {
            PhraseLanguage.ZH -> "不要" + names.joinToString("、")
            PhraseLanguage.JA -> names.joinToString("・") + "抜きでお願いします"
            PhraseLanguage.EN -> "No ${englishList(names, "or")}, please."
        }
    }

    fun header(lang: PhraseLanguage): String = headers.getValue(lang)

    fun disclaimer(lang: PhraseLanguage): String = disclaimers.getValue(lang)

    /** 设置页和点单页的一行摘要：“花生过敏 · 不要葱、香菜 · 微辣”。空档案返回 null。 */
    fun summary(profile: TasteProfile): String? = listOfNotNull(
        allergySentence(profile.allergens, PhraseLanguage.ZH),
        avoidSentence(profile.avoids, PhraseLanguage.ZH),
        profile.spice?.let { spice(it, PhraseLanguage.ZH) },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    private fun englishList(items: List<String>, conjunction: String): String = when (items.size) {
        1 -> items[0]
        2 -> "${items[0]} $conjunction ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + ", $conjunction " + items.last()
    }
}

/** 小票上的口味说明。[isAllergy] 的那条要醒目显示。 */
data class TasteNote(val phrase: Phrase, val isAllergy: Boolean)

private fun phrase(lang: PhraseLanguage, build: (PhraseLanguage) -> String?): Phrase? {
    val primary = build(lang) ?: return null
    val secondary = if (lang == PhraseLanguage.ZH) null else build(PhraseLanguage.ZH)
    return Phrase(primary, secondary)
}

/** 过敏、不要、辣度三条，按这个顺序；空的不写。 */
fun tasteNotes(profile: TasteProfile, lang: PhraseLanguage): List<TasteNote> = listOfNotNull(
    phrase(lang) { TastePhrases.allergySentence(profile.allergens, it) }?.let { TasteNote(it, isAllergy = true) },
    phrase(lang) { TastePhrases.avoidSentence(profile.avoids, it) }?.let { TasteNote(it, isAllergy = false) },
    profile.spice?.let { level -> phrase(lang) { TastePhrases.spice(level, it) } }?.let { TasteNote(it, isAllergy = false) },
)

/** 小票的一行菜：大字是菜单原文，小字是译名。 */
data class ReceiptLine(val nameOriginal: String, val nameTranslated: String?, val qty: Int)

data class Receipt(
    val language: PhraseLanguage,
    val header: Phrase,
    val lines: List<ReceiptLine>,
    val notes: List<TasteNote>,
    val disclaimer: Phrase,
)

/** 给店员看的小票。[menuLang] 是识别出的菜单语言（BCP 47）。 */
fun buildReceipt(lines: List<ReceiptLine>, taste: TasteProfile, menuLang: String?): Receipt {
    val lang = phraseLanguage(menuLang)
    return Receipt(
        language = lang,
        header = phrase(lang) { TastePhrases.header(it) }!!,
        lines = lines,
        notes = tasteNotes(taste, lang),
        disclaimer = phrase(lang) { TastePhrases.disclaimer(it) }!!,
    )
}

private val allergenSynonyms = mapOf(
    "peanuts" to Allergen.PEANUT,
    "crustacean" to Allergen.SHELLFISH,
    "crustaceans" to Allergen.SHELLFISH,
    "shrimp" to Allergen.SHELLFISH,
    "crab" to Allergen.SHELLFISH,
    "prawn" to Allergen.SHELLFISH,
    "eggs" to Allergen.EGG,
    "dairy" to Allergen.MILK,
    "lactose" to Allergen.MILK,
    "gluten" to Allergen.WHEAT,
    "soybean" to Allergen.SOY,
    "soya" to Allergen.SOY,
    "nuts" to Allergen.TREE_NUT,
    "tree_nuts" to Allergen.TREE_NUT,
    "soba" to Allergen.BUCKWHEAT,
)

/** 模型给的过敏原代码 -> [Allergen]。大小写、空格、连字符都不敏感，认识几个常见同义词；认不出返回 null。 */
fun allergenFromCode(code: String): Allergen? {
    val key = code.trim().lowercase().replace(Regex("[\\s-]+"), "_")
    return Allergen.entries.firstOrNull { it.code == key } ?: allergenSynonyms[key]
}

/** 菜品可能含有、而我又过敏的东西，按 [Allergen] 的顺序。 */
fun matchedAllergens(dishAllergens: List<String>, profile: TasteProfile): List<Allergen> {
    if (profile.allergens.isEmpty()) return emptyList()
    val found = dishAllergens.mapNotNull(::allergenFromCode).toSet()
    return profile.allergens.filter { it in found }.sortedBy { it.ordinal }
}

/** 这次点单实际用的口味：这次改过就用改的；否则开了“自动带上”才用档案。 */
fun effectiveTaste(profile: TasteProfile, override: TasteProfile?): TasteProfile = when {
    override != null -> override
    profile.autoApply -> profile
    else -> TasteProfile.EMPTY
}
