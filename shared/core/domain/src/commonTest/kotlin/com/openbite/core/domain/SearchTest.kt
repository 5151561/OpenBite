package com.openbite.core.domain

import com.openbite.core.data.DiningLogRepository
import com.openbite.core.model.DiningLog
import com.openbite.core.model.DiningLogItem
import com.openbite.core.model.DiningStatus
import com.openbite.core.model.LogSource
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchTest {
    @Test
    fun likePatternEscapesWildcards() {
        assertEquals("%みなと%", likePattern("  みなと "))
        assertEquals("%100\\%%", likePattern("100%"))
        assertEquals("%a\\_b%", likePattern("a_b"))
        assertEquals("%c\\\\d%", likePattern("c\\d"))
        assertNull(likePattern("   "))
        assertEquals(MAX_QUERY_LENGTH + 2, likePattern("x".repeat(200))!!.length)
    }

    @Test
    fun memorySearchKeepsDbOrderAndVisitNumbers() = runTest {
        val place = Place("p", "割烹 みなと", null, null, null)
        fun log(id: String, date: LocalDate, at: Long) = DiningLog(
            id, place, at, "Asia/Tokyo", date, DiningStatus.DONE, null, null, LogSource.USER,
            listOf(DiningLogItem("$id-i", null, "うに", Money(1, "JPY"), 1, null, null)),
        )
        val logs = listOf(log("new", LocalDate(2026, 10, 3), 2), log("old", LocalDate(2025, 11, 27), 1))
        var captured = ""
        val repo = object : DiningLogRepository {
            override fun observeLogs() = flowOf(logs)
            override suspend fun logsOfMilestone(milestoneId: String) = emptyList<DiningLog>()
            override suspend fun searchIds(pattern: String, limit: Int): List<String> {
                captured = pattern
                // 数据库可能返回已经不在列表里的 id（刚被删），要跳过。
                return listOf("new", "gone", "old")
            }
        }
        val result = SearchMemoriesUseCase(repo) { LocalDate(2026, 11, 17) }("うに")
        assertEquals(listOf("new", "old"), result.map { it.logId })
        assertEquals(2, result.first().visitNumber)
        assertEquals("%うに%", captured)
        assertTrue(SearchMemoriesUseCase(repo) { LocalDate(2026, 11, 17) }(" ").isEmpty())
    }

    @Test
    fun dishEditValidation() {
        val ok = assertIs<DishEditResult.Valid>(validateDishEdit(DishEditInput(" 海胆茶碗蒸 ", " 蒸物 ", "2,800", "jpy")))
        assertEquals("海胆茶碗蒸", ok.nameTranslated)
        assertEquals("蒸物", ok.category)
        assertEquals(Money(2800, "JPY"), ok.price)
        assertNull(assertIs<DishEditResult.Valid>(validateDishEdit(DishEditInput("a", "", " ", ""))).price)
        assertEquals(Money(1250, "USD"), assertIs<DishEditResult.Valid>(validateDishEdit(DishEditInput("a", "", "12.5", "USD"))).price)
        assertEquals(DishEditError.BAD_PRICE, assertIs<DishEditResult.Invalid>(validateDishEdit(DishEditInput("a", "", "12.5", "JPY"))).error)
        assertEquals(DishEditError.BAD_CURRENCY, assertIs<DishEditResult.Invalid>(validateDishEdit(DishEditInput("a", "", "100", "¥"))).error)
        assertEquals(DishEditError.NAME_TOO_LONG, assertIs<DishEditResult.Invalid>(validateDishEdit(DishEditInput("x".repeat(121), "", "", ""))).error)
        assertEquals(DishEditError.CATEGORY_TOO_LONG, assertIs<DishEditResult.Invalid>(validateDishEdit(DishEditInput("a", "x".repeat(61), "", ""))).error)
    }
}
