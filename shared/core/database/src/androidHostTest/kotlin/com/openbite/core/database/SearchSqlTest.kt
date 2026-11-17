package com.openbite.core.database

import com.openbite.core.database.dao.SEARCH_LOGS_SQL
import com.openbite.core.database.dao.SEARCH_PLACES_SQL
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 在 JVM 上用 SQLite 跑真实的搜索 SQL（表按导出的 schemas/3.json 建）。 */
class SearchSqlTest {
    private lateinit var db: Connection
    private val schemaDir = File(System.getProperty("user.dir"), "schemas/com.openbite.core.database.OpenBiteDatabase")

    @BeforeTest
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        val database = Json.parseToJsonElement(File(schemaDir, "3.json").readText()).jsonObject.getValue("database").jsonObject
        database.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            exec(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }
        exec("INSERT INTO place VALUES ('minato', '割烹 みなと', NULL, NULL, '京都市中京区', 0, NULL)")
        exec("INSERT INTO place VALUES ('tori', '炭火焼鳥 とり松', NULL, NULL, '先斗町', 0, NULL)")
        exec("INSERT INTO place VALUES ('pct', '100% Cafe', NULL, NULL, NULL, 0, NULL)")
        exec("INSERT INTO place_tag VALUES ('tori', '深夜食堂')")
        exec("INSERT INTO dish (id, placeId, scanId, category, nameOriginal, nameTranslated, priceMinor, currency, allergensJson) VALUES ('d1', 'minato', NULL, '', 'うに茶碗蒸し', '海胆茶碗蒸', 2800, 'JPY', '[]')")
        exec("INSERT INTO person VALUES ('wife', '老婆')")
        exec("INSERT INTO milestone VALUES ('love', '恋爱纪念日', 'anniversary', '2024-11-27', 'yearly', 7)")
        exec(log("l1", "minato", 1, "love", "她说明年还要来"))
        exec(log("l2", "minato", 2, null, null))
        exec(log("l3", "tori", 3, null, null))
        exec("INSERT INTO dining_log_item (id, logId, dishId, nameSnapshot, priceMinorSnapshot, currencySnapshot, qty, rating, comment, nameTranslatedSnapshot) VALUES ('i1', 'l2', 'd1', 'うに茶碗蒸し', 2800, 'JPY', 1, 5, '入口即化', '海胆茶碗蒸')")
        exec("INSERT INTO dining_log_person VALUES ('l3', 'wife')")
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun log(id: String, place: String, at: Long, milestone: String?, note: String?) =
        "INSERT INTO dining_log (id, placeId, visitedAt, timezone, localDate, status, milestoneId, note, createdBy, createdAt, updatedAt) " +
            "VALUES ('$id', '$place', $at, 'Asia/Tokyo', '2026-01-01', 'done', ${milestone?.let { "'$it'" } ?: "NULL"}, ${note?.let { "'$it'" } ?: "NULL"}, 'user', 0, 0)"

    private fun exec(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    /** 把 Room 的 :pattern / :limit 换成字面量再跑（pattern 里的单引号转义）。 */
    private fun run(sql: String, pattern: String, limit: Int = 50): List<String> {
        val bound = sql.replace(":pattern", "'" + pattern.replace("'", "''") + "'").replace(":limit", limit.toString())
        return db.createStatement().use { st ->
            st.executeQuery(bound).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
    }

    @Test
    fun logsMatchEveryField() {
        assertEquals(listOf("l2", "l1"), run(SEARCH_LOGS_SQL, "%みなと%"))
        assertEquals(listOf("l2"), run(SEARCH_LOGS_SQL, "%海胆%"))
        assertEquals(listOf("l2"), run(SEARCH_LOGS_SQL, "%入口%"))
        assertEquals(listOf("l1"), run(SEARCH_LOGS_SQL, "%明年%"))
        assertEquals(listOf("l1"), run(SEARCH_LOGS_SQL, "%恋爱%"))
        assertEquals(listOf("l3"), run(SEARCH_LOGS_SQL, "%老婆%"))
        assertEquals(listOf("l3"), run(SEARCH_LOGS_SQL, "%老婆%", limit = 1))
    }

    @Test
    fun placesMatchNameAddressTagDish() {
        assertEquals(listOf("minato"), run(SEARCH_PLACES_SQL, "%中京%"))
        assertEquals(listOf("tori"), run(SEARCH_PLACES_SQL, "%深夜%"))
        assertEquals(listOf("minato"), run(SEARCH_PLACES_SQL, "%茶碗%"))
        assertEquals(listOf("minato"), run(SEARCH_PLACES_SQL, "%海胆%"))
    }

    @Test
    fun escapedWildcardsAreLiteral() {
        // 搜 “100%”：% 被转义，只命中名字里真有 100% 的店。
        assertEquals(listOf("pct"), run(SEARCH_PLACES_SQL, "%100\\%%"))
        // 不转义的 % 会命中全部，这里确认转义确实生效。
        assertEquals(emptyList(), run(SEARCH_PLACES_SQL, "%\\_%"))
    }
}
