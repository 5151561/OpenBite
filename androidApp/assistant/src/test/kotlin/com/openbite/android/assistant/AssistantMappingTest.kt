package com.openbite.android.assistant

import com.openbite.core.domain.AssistantDish
import com.openbite.core.domain.AssistantMeal
import com.openbite.core.domain.AssistantMenu
import com.openbite.core.domain.AssistantMenuDish
import com.openbite.core.domain.AssistantMilestone
import com.openbite.core.domain.AssistantPlace
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssistantMappingTest {
    private val meal = AssistantMeal(
        logId = "log-1",
        placeId = "place-minato",
        placeName = "割烹 みなと",
        localDate = LocalDate(2025, 11, 27),
        localTime = LocalTime(19, 30),
        isDraft = true,
        createdByAssistant = true,
        milestoneId = "ms-love",
        milestoneTitle = "恋爱纪念日",
        rating = 4.5,
        note = "第一行\n第二行",
        companions = listOf("小林"),
        dishes = listOf(AssistantDish("dish-uni", "うに茶碗蒸し", "海胆茶碗蒸", 2, Money(2800, "JPY"), 5, "甜")),
        menuTotal = Money(5600, "JPY"),
    )

    @Test
    fun mealKeepsEveryField() {
        val log = meal.toFunction()
        assertEquals(java.time.LocalDate.of(2025, 11, 27), log.date)
        assertEquals(java.time.LocalTime.of(19, 30), log.time)
        assertEquals(true, log.isDraft)
        assertEquals(true, log.createdByAssistant)
        assertEquals("恋爱纪念日", log.milestoneTitle)
        assertEquals("第一行\n第二行", log.note)
        assertEquals(MoneyAmount(5600, "JPY"), log.menuTotal)
        val dish = log.dishes.single()
        assertEquals(MoneyAmount(2800, "JPY"), dish.price)
        assertEquals(2, dish.quantity)
    }

    @Test
    fun milestoneTypeIsLowercaseCode() {
        val m = AssistantMilestone(
            "ms-love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), LocalDate(2026, 11, 27),
            10, 2, true, listOf("小林"), listOf(meal),
        ).toFunction()
        assertEquals("anniversary", m.type)
        assertEquals(java.time.LocalDate.of(2026, 11, 27), m.nextDate)
        assertEquals("log-1", m.previousMeals.single().logId)
    }

    @Test
    fun placeAndMenuWithoutOptionalValues() {
        val place = AssistantPlace("p", "店", null, null, null, emptyList(), 0, null, null)
        val menu = AssistantMenu(place, null, listOf(AssistantMenuDish("d", "", "冷奴", "", null, emptyList(), 0, null))).toFunction()
        assertNull(menu.place.lastVisit)
        assertNull(menu.dishes.single().price)
    }
}
