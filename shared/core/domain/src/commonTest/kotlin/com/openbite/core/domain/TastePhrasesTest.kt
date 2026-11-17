package com.openbite.core.domain

import com.openbite.core.model.Allergen
import com.openbite.core.model.AvoidItem
import com.openbite.core.model.SpiceLevel
import com.openbite.core.model.TasteProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TastePhrasesTest {
    private val design = TasteProfile(
        allergens = listOf(Allergen.PEANUT),
        avoids = listOf(AvoidItem.GREEN_ONION, AvoidItem.CILANTRO),
        spice = SpiceLevel.MILD,
    )

    @Test
    fun languageTagsMapToTableKeys() {
        assertEquals("ja", phraseTag("ja"))
        assertEquals("ja", phraseTag("ja-JP"))
        assertEquals("en", phraseTag("EN_us"))
        assertEquals("pt", phraseTag("pt-BR"))
        assertEquals("zh-Hans", phraseTag("zh"))
        assertEquals("zh-Hans", phraseTag("zh-CN"))
        assertEquals("zh-Hans", phraseTag("zh-Hans-HK"))
        assertEquals("zh-Hant", phraseTag("zh-TW"))
        assertEquals("zh-Hant", phraseTag("zh_HK"))
        assertEquals("zh-Hant", phraseTag("zh-Hant"))
        assertEquals("id", phraseTag("in-ID"), "Java 的旧代码")
        assertEquals("sv", phraseTag("sv-SE"))
        assertNull(phraseTag(null))
        assertNull(phraseTag(" "))
        assertNull(phraseTag("und"))
        assertNull(phraseTag("12"))
    }

    @Test
    fun everyBuiltInTableIsComplete() {
        assertEquals(
            setOf("zh-Hans", "zh-Hant", "ja", "ko", "en", "fr", "de", "es", "it", "pt", "th", "vi", "id"),
            PhraseTables.builtIn.keys,
        )
        PhraseTables.builtIn.forEach { (tag, table) ->
            assertEquals(table, table.validated(), "$tag 不完整或有多余空白")
            assertEquals(Allergen.entries.size, table.allergens.size, tag)
            assertEquals(AvoidItem.entries.size, table.avoids.size, tag)
            assertEquals(SpiceLevel.entries.size, table.spice.size, tag)
            // 名字不重复（复制粘贴漏改会撞）。
            assertEquals(table.allergens.size, table.allergens.toSet().size, tag)
            assertEquals(table.avoids.size, table.avoids.toSet().size, tag)
        }
    }

    @Test
    fun japaneseNotesWithChineseUnderneath() {
        val notes = tasteNotes(design, PhraseTables.JA, PhraseTables.ZH_HANS)
        assertEquals(
            listOf(
                Phrase("ピーナッツアレルギーがあります", "花生过敏"),
                Phrase("ネギ・パクチー抜きでお願いします", "不要葱、香菜"),
                Phrase("辛さ控えめでお願いします", "微辣"),
            ),
            notes.map { it.phrase },
        )
        assertEquals(listOf(true, false, false), notes.map { it.isAllergy })
    }

    @Test
    fun listsJoinPerLanguage() {
        val three = listOf(Allergen.PEANUT, Allergen.EGG, Allergen.SHELLFISH)
        val two = listOf(AvoidItem.BEEF, AvoidItem.PORK)
        assertEquals("I'm allergic to peanuts, eggs, and shellfish.", TastePhrases.allergySentence(three, PhraseTables.EN))
        assertEquals("No beef or pork, please.", TastePhrases.avoidSentence(two, PhraseTables.EN))
        assertEquals("I'm allergic to sesame.", TastePhrases.allergySentence(listOf(Allergen.SESAME), PhraseTables.EN))
        assertEquals("Ich bin allergisch gegen Erdnüsse, Eier und Krebstiere.", TastePhrases.allergySentence(three, PhraseTables.DE))
        assertEquals("Sans bœuf ni porc, s'il vous plaît.", TastePhrases.avoidSentence(two, PhraseTables.FR))
        assertEquals("Sin ternera ni cerdo, por favor.", TastePhrases.avoidSentence(two, PhraseTables.ES))
        assertEquals("Saya alergi kacang tanah, telur, dan udang/kepiting.", TastePhrases.allergySentence(three, PhraseTables.ID))
        assertEquals("แพ้ถั่วลิสง ไข่ และสัตว์มีเปลือก (กุ้ง ปู)", TastePhrases.allergySentence(three, PhraseTables.TH))
        assertEquals("땅콩, 달걀, 갑각류(새우·게) 알레르기가 있어요.", TastePhrases.allergySentence(three, PhraseTables.KO))
        assertEquals("花生、蛋、虾蟹过敏", TastePhrases.allergySentence(three, PhraseTables.ZH_HANS))
    }

    @Test
    fun receiptUsesMenuLanguageAndUserLanguageUnderneath() {
        val receipt = buildReceipt(listOf(ReceiptLine("生ビール", "生啤", 2)), design, "ja", userLang = "zh-CN")
        assertEquals("ja", receipt.language.tag)
        assertEquals("注文をお願いします", receipt.header.primary)
        assertEquals("我要点这些", receipt.header.secondary)
        assertTrue("店員" in receipt.disclaimer.primary)
        assertEquals(2, receipt.lines.single().qty)

        val english = buildReceipt(emptyList(), design, "fr-FR", userLang = "en-GB")
        assertEquals("Je voudrais commander :", english.header.primary)
        assertEquals("I'd like to order:", english.header.secondary)
        val allergy = english.notes.first()
        assertTrue(allergy.isAllergy)
        assertEquals("I'm allergic to peanuts.", allergy.phrase.secondary, "过敏那条一定带对照行")
    }

    @Test
    fun sameLanguageHasNoSecondaryLine() {
        val receipt = buildReceipt(emptyList(), design, "zh-CN", userLang = "zh-Hans-CN")
        assertNull(receipt.secondaryTag)
        assertEquals(Phrase("我要点这些", null), receipt.header)
        assertEquals(Phrase("花生过敏", null), receipt.notes.first().phrase)
        // 简繁算两种语言。
        assertEquals("我要点这些", buildReceipt(emptyList(), design, "zh-TW", userLang = "zh-CN").header.secondary)
    }

    @Test
    fun unknownOrMissingMenuLanguageUsesEnglish() {
        assertEquals("en", buildReceipt(emptyList(), design, null, userLang = "zh-CN").language.tag)
        assertEquals("I'd like to order:", buildReceipt(emptyList(), design, "sv", userLang = "zh-CN").header.primary)
    }

    @Test
    fun userLanguageFallsBackToChinese() {
        assertEquals("en", userPhraseTag("en-US"))
        assertEquals("zh-Hant", userPhraseTag("zh-TW"))
        assertEquals("zh-Hans", userPhraseTag("ru-RU"))
    }

    @Test
    fun summaryUsesUserLanguage() {
        assertNull(TastePhrases.summary(TasteProfile.EMPTY, "zh-CN"))
        assertEquals("花生过敏 · 不要葱、香菜 · 微辣", TastePhrases.summary(design, "zh-CN"))
        assertEquals(
            "I'm allergic to peanuts. · No green onion or cilantro, please. · Only a little spicy, please.",
            TastePhrases.summary(design, "en-US"),
        )
    }

    @Test
    fun emptyProfileWritesNothing() {
        assertTrue(tasteNotes(TasteProfile.EMPTY, PhraseTables.JA, PhraseTables.ZH_HANS).isEmpty())
    }

    @Test
    fun allergenCodesAndSynonyms() {
        assertEquals(Allergen.TREE_NUT, allergenFromCode("tree_nut"))
        assertEquals(Allergen.TREE_NUT, allergenFromCode("Tree-Nuts"))
        assertEquals(Allergen.SHELLFISH, allergenFromCode(" shrimp "))
        assertEquals(Allergen.MILK, allergenFromCode("dairy"))
        assertEquals(Allergen.WHEAT, allergenFromCode("gluten"))
        assertNull(allergenFromCode("mustard"))
    }

    @Test
    fun matchingOnlyReportsMyAllergens() {
        val me = TasteProfile(allergens = listOf(Allergen.WHEAT, Allergen.SHELLFISH))
        assertEquals(listOf(Allergen.SHELLFISH, Allergen.WHEAT), matchedAllergens(listOf("wheat", "egg", "shrimp"), me))
        assertTrue(matchedAllergens(listOf("egg"), me).isEmpty())
        assertTrue(matchedAllergens(listOf("wheat"), TasteProfile.EMPTY).isEmpty())
    }

    @Test
    fun effectiveTasteRespectsOverrideAndAutoApply() {
        val override = TasteProfile(spice = SpiceLevel.HOT)
        assertEquals(override, effectiveTaste(design, override))
        assertEquals(design, effectiveTaste(design, null))
        assertEquals(TasteProfile.EMPTY, effectiveTaste(design.copy(autoApply = false), null))
    }

    @Test
    fun toggleKeepsEnumOrder() {
        val profile = TasteProfile.EMPTY.toggle(Allergen.FISH).toggle(Allergen.PEANUT)
        assertEquals(listOf(Allergen.PEANUT, Allergen.FISH), profile.allergens)
        assertFalse(Allergen.FISH in profile.toggle(Allergen.FISH).allergens)
    }
}
