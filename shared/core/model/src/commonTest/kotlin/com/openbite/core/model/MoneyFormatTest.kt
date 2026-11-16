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
    fun negativeAmount() {
        assertEquals("-¥500", formatMoney(Money(-500, "JPY")))
    }
}
