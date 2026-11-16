package com.openbite.android.feature.place

import com.openbite.android.core.designsystem.preview.PreviewData
import com.openbite.core.model.Dish
import com.openbite.core.model.DishStats
import com.openbite.core.model.Glyph
import com.openbite.core.model.GlyphTone
import com.openbite.core.model.MenuEntry
import com.openbite.core.model.MenuPhoto
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.MilestoneType
import com.openbite.core.model.Money
import com.openbite.core.model.Place
import com.openbite.core.model.PlaceBadge
import com.openbite.core.model.PlaceDetail
import com.openbite.core.model.PlaceMenu
import com.openbite.core.model.PlaceSummary
import com.openbite.core.model.PlaceVisit
import com.openbite.core.model.Recurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** @Preview 用的假数据，与种子数据一致。 */
internal object PlacePreviewData {
    private val minato = Place("minato", "割烹 みなと", null, null, "京都市中京区木屋町通", listOf("适合约会", "纪念日"))
    private val tori = Place("tori", "炭火焼鳥 とり松", null, null, "京都市中京区先斗町通", listOf("深夜食堂"))

    val places = listOf(
        PlaceSummary(tori, Glyph("鳥", GlyphTone.TERTIARY), PlaceBadge.Tonight, 6, 1, 1, PreviewData.today),
        PlaceSummary(Place("cafe", "喫茶 こもれび", null, null, null), Glyph("茶", GlyphTone.PRIMARY_FIXED), null, 9, 1, 1, LocalDate(2026, 11, 15)),
        PlaceSummary(Place("hinata", "麺屋 ひなた", null, null, null), Glyph("麺", GlyphTone.SECONDARY), PlaceBadge.Tag("深夜食堂"), 8, 1, 3, LocalDate(2026, 11, 14)),
        PlaceSummary(Place("tsubame", "洋食 つばめ", null, null, null), Glyph("洋", GlyphTone.NEUTRAL), null, 10, 1, 1, LocalDate(2026, 10, 26)),
        PlaceSummary(minato, Glyph("割", GlyphTone.PRIMARY), PlaceBadge.Milestone, 12, 2, 2, LocalDate(2026, 10, 3)),
    )

    private val love = Milestone("ms-love", "恋爱纪念日", MilestoneType.ANNIVERSARY, LocalDate(2024, 11, 27), Recurrence.YEARLY)
    private val minatoMemories = PreviewData.memories.filter { it.placeName == minato.name }

    val detail = PlaceDetail(
        place = minato,
        glyph = Glyph("割", GlyphTone.PRIMARY),
        area = "京都市中京区",
        visitCount = 2,
        visits = listOf(
            PlaceVisit(minatoMemories[0].copy(note = "换了秋天的菜单，西京烧比上次更好。", dishCount = 4, total = Money(9_200, "JPY")), null, 0),
            PlaceVisit(minatoMemories[1].copy(total = Money(18_400, "JPY")), love, 1),
        ),
        orderedDishes = listOf(
            DishStats("uni", "うに茶碗蒸し", "海胆茶碗蒸", 2, 5.0, false, LocalDate(2026, 10, 3)),
            DishStats("saikyo", "鰆の西京焼き", "西京烧鲅鱼", 2, 4.5, false, LocalDate(2026, 10, 3)),
            DishStats("tempura", "季節の天ぷら", "时令天妇罗", 1, 4.0, false, LocalDate(2026, 10, 3)),
        ),
    )

    private fun dish(id: String, original: String, translated: String, price: Long, category: String = "一品") =
        Dish(id, tori.id, category, original, translated, Money(price, "JPY"))

    private val tones = listOf(GlyphTone.TERTIARY, GlyphTone.PRIMARY_FIXED, GlyphTone.SECONDARY, GlyphTone.PRIMARY, GlyphTone.NEUTRAL)

    val menu = PlaceMenu(
        place = tori,
        entries = listOf(
            dish("1", "焼き鳥盛り合わせ", "烤鸡肉串拼盘", 980) to DishStats("1", "", "", 1, null, false, PreviewData.today),
            dish("2", "鯖の塩焼き", "盐烤青花鱼", 780) to null,
            dish("3", "だし巻き玉子", "高汤煎蛋卷", 580) to DishStats("3", "", "", 1, 5.0, false, PreviewData.today),
            dish("4", "海老の天ぷら", "炸虾天妇罗", 1200) to DishStats("4", "", "", 1, 4.0, false, PreviewData.today),
            dish("5", "冷奴", "冷豆腐", 380) to null,
            dish("6", "生ビール", "生啤", 550, "飲み物") to DishStats("6", "", "", 2, null, true, PreviewData.today),
        ).mapIndexed { i, (d, s) ->
            MenuEntry(d, Glyph(d.nameOriginal.first { it in '\u4E00'..'\u9FFF' }.toString(), tones[i % tones.size]), s)
        },
        photos = listOf(
            MenuPhoto(MenuScan("s1", tori.id, "seed/menus/torimatsu-1.jpg", 0), LocalDateTime(2026, 11, 17, 19, 31)),
        ),
    )
}
