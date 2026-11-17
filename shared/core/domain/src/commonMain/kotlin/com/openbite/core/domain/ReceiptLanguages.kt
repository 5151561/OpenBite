package com.openbite.core.domain

import com.openbite.core.data.platform.PreferencesStore
import com.openbite.core.model.ModelUse
import com.openbite.core.model.languageDisplayName
import com.openbite.core.network.PhrasePrompt
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** 校验用：键必须完全一样（多了、少了都不行）。 */
private val strictJson = Json { ignoreUnknownKeys = false }
private val cacheJson = Json { encodeDefaults = true }

private const val MAX_NAME = 40
private const val MAX_SHORT = 80
private const val MAX_SENTENCE = 200
private const val MAX_SEPARATOR = 4
private const val MAX_JOINER = 12

private fun String.fits(max: Int) = isNotBlank() && length <= max && '\n' !in this
private fun String.fitsSeparator(max: Int) = isNotEmpty() && length <= max && '\n' !in this
private fun String.hasOnePlaceholder() = split(PhraseTable.ITEMS).size == 2

/** 每项都符合要求的表；否则 null。文字两端的空白去掉（连接符保留空格）。 */
fun PhraseTable.validated(): PhraseTable? {
    val table = copy(
        header = header.trim(),
        allergens = allergens.map { it.trim() },
        avoids = avoids.map { it.trim() },
        spice = spice.map { it.trim() },
        allergyTemplate = allergyTemplate.trim(),
        avoidTemplate = avoidTemplate.trim(),
        disclaimer = disclaimer.trim(),
    )
    val ok = table.allergens.size == PhraseTable.ALLERGEN_COUNT &&
        table.avoids.size == PhraseTable.AVOID_COUNT &&
        table.spice.size == PhraseTable.SPICE_COUNT &&
        table.header.fits(MAX_SHORT) &&
        table.allergens.all { it.fits(MAX_NAME) } &&
        table.avoids.all { it.fits(MAX_NAME) } &&
        table.spice.all { it.fits(MAX_SHORT) } &&
        table.allergyTemplate.fits(MAX_SENTENCE) && table.allergyTemplate.hasOnePlaceholder() &&
        table.avoidTemplate.fits(MAX_SENTENCE) && table.avoidTemplate.hasOnePlaceholder() &&
        table.disclaimer.fits(MAX_SENTENCE) &&
        table.listSeparator.fitsSeparator(MAX_SEPARATOR) &&
        table.andWord.fitsSeparator(MAX_JOINER) &&
        table.orWord.fitsSeparator(MAX_JOINER)
    return table.takeIf { ok }
}

/** 模型输出 -> 句子表：取第一个 `{` 到最后一个 `}`，键和英文表完全一样、每项都过 [validated] 才算。 */
fun parsePhraseTable(text: String): PhraseTable? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { strictJson.decodeFromString<PhraseTable>(text.substring(start, end + 1)) }.getOrNull()?.validated()
}

fun PhraseTable.toJson(): String = cacheJson.encodeToString(this)

/**
 * 给店员看的小票用什么语言：
 * 1. 菜单语言在内置表里（中文简繁、日、韩、英、法、德、西、意、葡、泰、越、印尼）直接用；
 * 2. 否则先看缓存（AI 以前翻过的）；
 * 3. 再让用户选的模型把英文表翻过去，校验通过就缓存；
 * 4. 演示模式、没选模型、没网或翻译不合格：先用英文，[ReceiptLanguage.unavailable] 记下想要的语言，界面给“联网后重试”。
 */
class ResolveReceiptLanguageUseCase(
    private val preferences: PreferencesStore,
    private val activeModel: ActiveVisionModel,
) {
    suspend operator fun invoke(menuLang: String?): ReceiptLanguage {
        ReceiptLanguage.builtIn(menuLang)?.let { return it }
        val tag = phraseTag(menuLang) ?: return ReceiptLanguage.ENGLISH
        cached(tag)?.let { return ReceiptLanguage(tag, it, aiTranslated = true) }
        val translated = translate(tag) ?: return ReceiptLanguage.ENGLISH.copy(unavailable = tag)
        runCatching { preferences.cachePhraseTable(tag, translated.toJson()) }
        return ReceiptLanguage(tag, translated, aiTranslated = true)
    }

    /** 不联网能不能直接给出结果（内置表或缓存）。 */
    suspend fun offline(menuLang: String?): ReceiptLanguage? {
        ReceiptLanguage.builtIn(menuLang)?.let { return it }
        val tag = phraseTag(menuLang) ?: return ReceiptLanguage.ENGLISH
        return cached(tag)?.let { ReceiptLanguage(tag, it, aiTranslated = true) }
    }

    private suspend fun cached(tag: String): PhraseTable? =
        runCatching { preferences.cachedPhraseTable(tag) }.getOrNull()?.let(::parsePhraseTable)

    private suspend fun translate(tag: String): PhraseTable? {
        val active = try {
            activeModel.resolve()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        // 演示模式不联网。
        if (active.use is ModelUse.Demo) return null
        return try {
            val output = active.model.completeText(
                apiKey = active.apiKey,
                system = PhrasePrompt.system(languageDisplayName(tag, "en"), tag),
                user = PhrasePrompt.user(PhraseTables.EN.toJson()),
            )
            parsePhraseTable(output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
