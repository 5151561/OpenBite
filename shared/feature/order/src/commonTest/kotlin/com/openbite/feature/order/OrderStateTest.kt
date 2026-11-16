package com.openbite.feature.order

import com.openbite.core.domain.MenuStatus
import com.openbite.core.domain.OrderDish
import com.openbite.core.domain.OrderMenu
import com.openbite.core.model.Allergen
import com.openbite.core.model.Money
import com.openbite.core.model.TasteProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderStateTest {
    private fun dish(key: String, category: String, price: Long, vararg allergens: String) =
        OrderDish(key, key, "", category, Money(price, "JPY"), allergens.toList(), null)

    private val menu = OrderMenu(
        null, "とり松", null, "ja", "JPY",
        listOf(
            dish("焼き鳥盛り合わせ", "焼き鳥", 980, "soy"),
            dish("海老の天ぷら", "揚げ物", 1200, "shellfish", "wheat"),
            dish("生ビール", "飲み物", 550, "wheat"),
        ),
        MenuStatus.SCANNING,
    )

    @Test
    fun warningsUseOverrideWhenSet() {
        val profile = TasteProfile(allergens = listOf(Allergen.SHELLFISH))
        val state = orderState(menu, mapOf("生ビール" to 2), profile, override = null, category = null)
        assertEquals(listOf(emptyList(), listOf(Allergen.SHELLFISH), emptyList()), state.items.map { it.warnings })
        val overridden = orderState(menu, emptyMap(), profile, TasteProfile(allergens = listOf(Allergen.WHEAT)), null)
        assertEquals(listOf(emptyList(), listOf(Allergen.WHEAT), listOf(Allergen.WHEAT)), overridden.items.map { it.warnings })
    }

    @Test
    fun totalsCountsAndCategories() {
        val state = orderState(menu, mapOf("生ビール" to 2, "焼き鳥盛り合わせ" to 1), TasteProfile.EMPTY, null, "飲み物")
        assertEquals(Money(2080, "JPY"), state.total)
        assertEquals(3, state.count)
        assertEquals(listOf("焼き鳥", "揚げ物", "飲み物"), state.categories)
        assertEquals(listOf("生ビール"), state.visibleItems.map { it.dish.key })
        // 分类不在菜单里时退回“全部”。
        assertNull(orderState(menu, emptyMap(), TasteProfile.EMPTY, null, "甘味").category)
    }
}
