package com.openbite.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 语言都钉死，不受跑测试的机器影响。 */
class MoneyFormatTest {
    private val zh = "zh-CN"
    private val en = "en-US"
    private val de = "de-DE"
    private val nbsp = '\u00A0'

    @Test
    fun decimalsFollowIso4217() {
        assertEquals(0, currencyDecimals("JPY"))
        assertEquals(0, currencyDecimals("KRW"))
        assertEquals(2, currencyDecimals("EUR"))
        assertEquals(2, currencyDecimals("thb"))
        assertEquals(3, currencyDecimals("KWD"))
        assertEquals(3, currencyDecimals("BHD"))
        // 不认识的、没有小数位定义的都按 2。
        assertEquals(2, currencyDecimals("ABC"))
        assertNull(currencyFractionDigits("XXX"))
        assertTrue(isKnownCurrency("CNY"))
        assertTrue(!isKnownCurrency("ABC"))
    }

    @Test
    fun formatsWithPhoneLocale() {
        assertEquals("€12.50", formatMoney(Money(1250, "EUR"), zh))
        assertEquals("JP¥4,246", formatMoney(Money(4246, "JPY"), zh))
        assertEquals("¥12.50", formatMoney(Money(1250, "CNY"), zh))
        assertEquals("12,50$nbsp€", formatMoney(Money(1250, "EUR"), de))
        assertEquals("4.246$nbsp¥", formatMoney(Money(4246, "JPY"), de))
        assertEquals("$12.50", formatMoney(Money(1250, "USD"), en))
        assertEquals("¥1,234,567", formatMoney(Money(1_234_567, "JPY"), en))
        assertEquals("KWD1.250", formatMoney(Money(1250, "KWD"), en).replace(nbsp.toString(), ""))
    }

    @Test
    fun unknownCodesShowCodeAndTwoDecimals() {
        assertEquals("XXX 12.00", formatMoney(Money(1200, "XXX"), en))
        assertEquals("ABC 1,200.00", formatMoney(Money(120_000, "ABC"), en))
        assertEquals("ABC 12,00", formatMoney(Money(1200, "ABC"), de))
    }

    @Test
    fun negativeAmount() {
        assertEquals("-¥500", formatMoney(Money(-500, "JPY"), en))
    }

    @Test
    fun parsesDotAndCommaDecimals() {
        assertEquals(Money(1250, "EUR"), parseMoneyInput("12.50", "EUR", zh))
        assertEquals(Money(1250, "EUR"), parseMoneyInput("12,50", "EUR", zh))
        assertEquals(Money(1250, "EUR"), parseMoneyInput("12,50", "EUR", de))
        assertEquals(Money(1250, "USD"), parseMoneyInput("12.5", "usd", en))
        assertEquals(Money(1205, "USD"), parseMoneyInput("$12.05", "USD", en))
        assertEquals(Money(1250, "KWD"), parseMoneyInput("1.25", "KWD", en))
    }

    @Test
    fun parsesLocaleGrouping() {
        assertEquals(Money(4246, "JPY"), parseMoneyInput("¥4,246", "JPY", zh))
        assertEquals(Money(4246, "JPY"), parseMoneyInput("4.246", "JPY", de))
        assertEquals(Money(123_450, "EUR"), parseMoneyInput("1.234,50", "EUR", de))
        assertEquals(Money(123_450, "USD"), parseMoneyInput("1,234.50", "USD", en))
        assertEquals(Money(123_400, "USD"), parseMoneyInput("1,234", "USD", en))
        assertEquals(Money(123_400, "EUR"), parseMoneyInput("1.234", "EUR", de))
        assertEquals(Money(123_450, "EUR"), parseMoneyInput("1 234,50 €", "EUR", de))
        assertEquals(Money(10_000_000, "INR"), parseMoneyInput("1,00,000", "INR", "en-IN"))
    }

    @Test
    fun rejectsBadInput() {
        assertNull(parseMoneyInput("12.5", "JPY", zh), "日元没有小数")
        assertNull(parseMoneyInput("12.345", "EUR", zh), "小数位太多")
        assertNull(parseMoneyInput("1,234", "EUR", de), "de 下逗号是小数点，三位小数太多")
        assertNull(parseMoneyInput("-5", "USD", en))
        assertNull(parseMoneyInput("", "JPY", zh))
        assertNull(parseMoneyInput("¥", "JPY", zh))
        assertNull(parseMoneyInput("1.2.3", "USD", en))
        assertNull(parseMoneyInput("1,2.3,4", "USD", en))
    }

    @Test
    fun inputTextUsesLocaleDecimalSeparator() {
        assertEquals("4246", moneyInputText(Money(4246, "JPY"), zh))
        assertEquals("12.05", moneyInputText(Money(1205, "USD"), zh))
        assertEquals("12,05", moneyInputText(Money(1205, "EUR"), de))
        assertEquals("1.250", moneyInputText(Money(1250, "KWD"), en))
        // 来回转一圈不变。
        assertEquals(Money(1205, "EUR"), parseMoneyInput(moneyInputText(Money(1205, "EUR"), de), "EUR", de))
    }

    @Test
    fun currencyNamesAndCountries() {
        assertEquals("EUR · 欧元", currencyLabel("EUR", zh))
        assertEquals("JPY", currencyForCountry("JP"))
        assertEquals("EUR", currencyForCountry("de"))
        assertEquals("CNY", currencyForCountry("CN"))
        assertNull(currencyForCountry("ZZ"))
        val all = availableCurrencyCodes()
        assertTrue("JPY" in all && "EUR" in all && "THB" in all)
        assertTrue("DEM" !in all, "不列已经停用的币种")
    }
}
