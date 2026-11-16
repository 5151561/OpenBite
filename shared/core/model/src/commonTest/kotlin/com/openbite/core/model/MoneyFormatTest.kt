package com.openbite.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyFormatTest {
    @Test
    fun yenHasNoDecimalsAndGroupsThousands() {
        assertEquals("¥18,400", formatMoney(Money(18_400, "JPY")))
        assertEquals("¥980", formatMoney(Money(980, "JPY")))
        assertEquals("¥1,234,567", formatMoney(Money(1_234_567, "JPY")))
    }

    @Test
    fun twoDecimalCurrencies() {
        assertEquals("$12.50", formatMoney(Money(1250, "USD")))
        assertEquals("€0.05", formatMoney(Money(5, "EUR")))
        assertEquals("THB 1,200.00", formatMoney(Money(120_000, "THB")))
    }

    @Test
    fun parsesUserInput() {
        assertEquals(Money(4246, "JPY"), parseMoneyInput("¥4,246", "JPY"))
        assertEquals(Money(1250, "USD"), parseMoneyInput("12.5", "usd"))
        assertEquals(Money(1205, "USD"), parseMoneyInput("$12.05", "USD"))
        assertEquals(null, parseMoneyInput("12.5", "JPY"))
        assertEquals(null, parseMoneyInput("", "JPY"))
        assertEquals(null, parseMoneyInput("1.2.3", "USD"))
        assertEquals("4246", moneyInputText(Money(4246, "JPY")))
        assertEquals("12.05", moneyInputText(Money(1205, "USD")))
    }

    @Test
    fun negativeAmount() {
        assertEquals("-¥500", formatMoney(Money(-500, "JPY")))
    }
}
