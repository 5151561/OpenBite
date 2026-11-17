package com.openbite.core.domain

import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile
import com.openbite.core.model.currentLocaleTag

/** 一句话：[primary] 用菜单语言给店员看，[secondary] 是用户自己的语言（手机语言）；两者相同时 [secondary] 为 null。 */
data class Phrase(val primary: String, val secondary: String?)

/**
 * 小票上的固定句子，按 [PhraseTable] 拼。不依赖模型，离线可用。
 * [lang] 都是 [phraseTag] 规范过的键；不在内置表里的按英文。
 */
object TastePhrases {
    private fun table(lang: String?): PhraseTable = PhraseTables.builtIn(lang) ?: PhraseTables.EN

    fun allergenName(allergen: Allergen, table: PhraseTable): String = table.allergens[allergen.ordinal]

    fun avoidName(avoid: AvoidItem, table: PhraseTable): String = table.avoids[avoid.ordinal]

    fun spice(level: SpiceLevel, table: PhraseTable): String = table.spice[level.ordinal]

    fun allergySentence(allergens: List<Allergen>, table: PhraseTable): String? =
        table.allergySentence(allergens.map { allergenName(it, table) })

    fun avoidSentence(avoids: List<AvoidItem>, table: PhraseTable): String? =
        table.avoidSentence(avoids.map { avoidName(it, table) })

    fun allergySentence(allergens: List<Allergen>, lang: String): String? = allergySentence(allergens, table(lang))

    fun avoidSentence(avoids: List<AvoidItem>, lang: String): String? = avoidSentence(avoids, table(lang))

    /**
     * 设置页和点单页的一行摘要，用用户自己的语言（[localeTag]，默认手机语言）：
     * “花生过敏 · 不要葱、香菜 · 微辣”。空档案返回 null。
     */
    fun summary(profile: TasteProfile, localeTag: String = currentLocaleTag()): String? {
        val table = table(userPhraseTag(localeTag))
        return listOfNotNull(
            allergySentence(profile.allergens, table),
            avoidSentence(profile.avoids, table),
            profile.spice?.let { spice(it, table) },
        ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

/** 用户自己的语言对应的内置表键：手机语言在内置表里就用它，否则用中文（App 默认界面语言）。 */
fun userPhraseTag(localeTag: String = currentLocaleTag()): String =
    phraseTag(localeTag)?.takeIf { it in PhraseTables.builtIn } ?: "zh-Hans"

/** 小票上的口味说明。[isAllergy] 的那条要醒目显示。 */
data class TasteNote(val phrase: Phrase, val isAllergy: Boolean)

private fun phrase(primary: PhraseTable, secondary: PhraseTable?, build: (PhraseTable) -> String?): Phrase? {
    val text = build(primary) ?: return null
    return Phrase(text, secondary?.let(build)?.takeIf { it != text })
}

/** 过敏、不要、辣度三条，按这个顺序；空的不写。[secondary] 为 null 时只写一种语言。过敏那条一定带对照行（两种语言不同时）。 */
fun tasteNotes(profile: TasteProfile, primary: PhraseTable, secondary: PhraseTable?): List<TasteNote> = listOfNotNull(
    phrase(primary, secondary) { TastePhrases.allergySentence(profile.allergens, it) }?.let { TasteNote(it, isAllergy = true) },
    phrase(primary, secondary) { TastePhrases.avoidSentence(profile.avoids, it) }?.let { TasteNote(it, isAllergy = false) },
    profile.spice?.let { level -> phrase(primary, secondary) { TastePhrases.spice(level, it) } }?.let { TasteNote(it, isAllergy = false) },
)

/** 小票的一行菜：大字是菜单原文，小字是译名。 */
data class ReceiptLine(val nameOriginal: String, val nameTranslated: String?, val qty: Int)

/**
 * 小票用哪张句子表。
 * - [tag]：实际用的表（[phraseTag] 的键）；
 * - [aiTranslated]：这张表是 AI 翻的（界面标“Deutsch · AI 翻译”）；
 * - [unavailable]：菜单语言翻不了（没网、没选模型、翻译失败），先用英文；界面提示并给“联网后重试”。
 */
data class ReceiptLanguage(
    val tag: String,
    val table: PhraseTable,
    val aiTranslated: Boolean = false,
    val unavailable: String? = null,
) {
    companion object {
        val ENGLISH = ReceiptLanguage("en", PhraseTables.EN)

        /** 菜单语言在内置表里就直接用；不认识语言用英文；要 AI 翻的返回 null。 */
        fun builtIn(menuLang: String?): ReceiptLanguage? {
            val tag = phraseTag(menuLang) ?: return ENGLISH
            return PhraseTables.builtIn(tag)?.let { ReceiptLanguage(tag, it) }
        }
    }
}

data class Receipt(
    val language: ReceiptLanguage,
    /** 对照行用的语言（用户自己的语言）；和小票语言相同时为 null。 */
    val secondaryTag: String?,
    val header: Phrase,
    val lines: List<ReceiptLine>,
    val notes: List<TasteNote>,
    val disclaimer: Phrase,
)

/** 给店员看的小票。主语言是 [language]，对照行用 [userLang]（手机语言，不在内置表时用中文）。 */
fun buildReceipt(lines: List<ReceiptLine>, taste: TasteProfile, language: ReceiptLanguage, userLang: String = currentLocaleTag()): Receipt {
    val secondaryTag = userPhraseTag(userLang).takeIf { it != language.tag }
    val secondary = PhraseTables.builtIn(secondaryTag)
    val primary = language.table
    return Receipt(
        language = language,
        secondaryTag = secondaryTag,
        header = phrase(primary, secondary) { it.header }!!,
        lines = lines,
        notes = tasteNotes(taste, primary, secondary),
        disclaimer = phrase(primary, secondary) { it.disclaimer }!!,
    )
}

/** 只用内置表（预览、测试）：[menuLang] 不在内置表时按英文。 */
fun buildReceipt(lines: List<ReceiptLine>, taste: TasteProfile, menuLang: String?, userLang: String = currentLocaleTag()): Receipt =
    buildReceipt(lines, taste, ReceiptLanguage.builtIn(menuLang) ?: ReceiptLanguage.ENGLISH, userLang)

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
