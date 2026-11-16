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
    fun languageTagsMapToPhraseLanguages() {
        assertEquals(PhraseLanguage.JA, phraseLanguage("ja"))
        assertEquals(PhraseLanguage.JA, phraseLanguage("ja-JP"))
        assertEquals(PhraseLanguage.EN, phraseLanguage("EN_us"))
        assertEquals(PhraseLanguage.ZH, phraseLanguage("ko"))
        assertEquals(PhraseLanguage.ZH, phraseLanguage(null))
    }

    @Test
    fun japaneseNotesMatchDesign() {
        val notes = tasteNotes(design, PhraseLanguage.JA)
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
    fun englishListsReadNaturally() {
        val profile = TasteProfile(
            allergens = listOf(Allergen.PEANUT, Allergen.EGG, Allergen.SHELLFISH),
            avoids = listOf(AvoidItem.BEEF, AvoidItem.PORK),
        )
        assertEquals("I'm allergic to peanuts, eggs, and shellfish.", TastePhrases.allergySentence(profile.allergens, PhraseLanguage.EN))
        assertEquals("No beef or pork, please.", TastePhrases.avoidSentence(profile.avoids, PhraseLanguage.EN))
        assertEquals("I'm allergic to sesame.", TastePhrases.allergySentence(listOf(Allergen.SESAME), PhraseLanguage.EN))
    }

    @Test
    fun unknownLanguageFallsBackToChineseOnly() {
        val receipt = buildReceipt(listOf(ReceiptLine("김치찌개", "泡菜汤", 1)), design, "ko")
        assertEquals(PhraseLanguage.ZH, receipt.language)
        assertEquals(Phrase("我要点这些", null), receipt.header)
        assertEquals(Phrase("花生过敏", null), receipt.notes.first().phrase)
        assertNull(receipt.disclaimer.secondary)
    }

    @Test
    fun receiptUsesMenuLanguageWithChineseUnderneath() {
        val receipt = buildReceipt(listOf(ReceiptLine("生ビール", "生啤", 2)), design, "ja")
        assertEquals("注文をお願いします", receipt.header.primary)
        assertEquals("我要点这些", receipt.header.secondary)
        assertTrue("店員" in receipt.disclaimer.primary)
        assertEquals(2, receipt.lines.single().qty)
    }

    @Test
    fun emptyProfileWritesNothing() {
        assertTrue(tasteNotes(TasteProfile.EMPTY, PhraseLanguage.JA).isEmpty())
        assertNull(TastePhrases.summary(TasteProfile.EMPTY))
        assertEquals("花生过敏 · 不要葱、香菜 · 微辣", TastePhrases.summary(design))
    }

    @Test
    fun everyEnumHasAPhraseInEveryLanguage() {
        PhraseLanguage.entries.forEach { lang ->
            Allergen.entries.forEach { assertTrue(TastePhrases.allergenName(it, lang).isNotBlank()) }
            AvoidItem.entries.forEach { assertTrue(TastePhrases.avoidName(it, lang).isNotBlank()) }
            SpiceLevel.entries.forEach { assertTrue(TastePhrases.spice(it, lang).isNotBlank()) }
        }
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
