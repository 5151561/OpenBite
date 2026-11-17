package com.openbite.android.core.designsystem.text

import kotlin.test.Test
import kotlin.test.assertEquals

/** 英文界面“2nd anniversary”的序数。 */
class OrdinalTest {
    @Test
    fun englishOrdinals() {
        assertEquals(
            listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "22nd", "101st", "111th"),
            listOf(1, 2, 3, 4, 11, 12, 13, 21, 22, 101, 111).map(PlainText::ordinal),
        )
    }
}
