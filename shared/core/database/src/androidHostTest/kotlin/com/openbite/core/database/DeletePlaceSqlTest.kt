package com.openbite.core.database

import com.openbite.core.database.dao.DELETE_PLACE_STATEMENTS
import com.openbite.core.database.dao.PLACE_PHOTO_PATHS_SQL
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

/**
 * 在 JVM 上用 SQLite 跑删除店铺的真实 SQL（表按导出的 schemas/2.json 建）。
 * 故意不开 foreign_keys：确认不靠级联也能删干净；开着跑一遍确认不会违反外键。
 */
class DeletePlaceSqlTest {
    private lateinit var db: Connection
    private val schemaDir = File(System.getProperty("user.dir"), "schemas/com.openbite.core.database.OpenBiteDatabase")

    private fun open(foreignKeys: Boolean) {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("PRAGMA foreign_keys = ${if (foreignKeys) "ON" else "OFF"}")
        val database = Json.parseToJsonElement(File(schemaDir, "2.json").readText()).jsonObject.getValue("database").jsonObject
        database.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            exec(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
        }
        exec("INSERT INTO place VALUES ('minato', '割烹 みなと', NULL, NULL, NULL, 0)")
        exec("INSERT INTO place VALUES ('tori', '炭火焼鳥 とり松', NULL, NULL, NULL, 0)")
        exec("INSERT INTO place_tag VALUES ('minato', '纪念日')")
        exec("INSERT INTO place_tag VALUES ('tori', '深夜食堂')")
        exec("INSERT INTO menu_scan (id, placeId, photoPath, model, sourceLang, targetLang, rawJson, createdAt) VALUES ('s1', 'minato', 'photos/s1.jpg', 'sample', 'ja', 'zh-CN', '{}', 0)")
        exec("INSERT INTO menu_scan (id, placeId, photoPath, model, sourceLang, targetLang, rawJson, createdAt) VALUES ('s2', 'tori', 'photos/s2.jpg', 'sample', 'ja', 'zh-CN', '{}', 0)")
        exec(dish("d1", "minato", "s1"))
        exec(dish("d2", "minato", "s1"))
        exec(dish("d3", "tori", "s2"))
        exec("INSERT INTO person VALUES ('wife', '老婆')")
        exec("INSERT INTO milestone VALUES ('love', '恋爱纪念日', 'anniversary', '2024-11-27', 'yearly', 7)")
        exec("INSERT INTO milestone_person VALUES ('love', 'wife')")
        exec(log("l1", "minato", "love"))
        exec(log("l2", "tori", "love"))
        exec(item("i1", "l1", "d1"))
        exec(item("i2", "l2", "d3"))
        exec("INSERT INTO dining_log_person VALUES ('l1', 'wife')")
        exec("INSERT INTO dining_log_person VALUES ('l2', 'wife')")
        exec("INSERT INTO photo (id, logId, path, takenAt, kind) VALUES ('p1', 'l1', 'photos/p1.jpg', 0, 'meal')")
        exec("INSERT INTO photo (id, logId, path, takenAt, kind) VALUES ('r1', 'l1', 'photos/r1.jpg', 0, 'receipt')")
        exec("INSERT INTO photo (id, logId, path, takenAt, kind) VALUES ('p2', 'l2', 'photos/p2.jpg', 0, 'meal')")
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun dish(id: String, place: String, scan: String) =
        "INSERT INTO dish (id, placeId, scanId, category, nameOriginal, nameTranslated, priceMinor, currency, allergensJson) VALUES ('$id', '$place', '$scan', '', '$id', '$id', 100, 'JPY', '[]')"

    private fun log(id: String, place: String, milestone: String?) =
        "INSERT INTO dining_log (id, placeId, visitedAt, timezone, localDate, status, milestoneId, note, createdBy, createdAt, updatedAt) " +
            "VALUES ('$id', '$place', 0, 'Asia/Tokyo', '2026-01-01', 'draft', ${milestone?.let { "'$it'" } ?: "NULL"}, NULL, 'user', 0, 0)"

    private fun item(id: String, logId: String, dishId: String) =
        "INSERT INTO dining_log_item (id, logId, dishId, nameSnapshot, priceMinorSnapshot, currencySnapshot, qty, rating, comment, nameTranslatedSnapshot) " +
            "VALUES ('$id', '$logId', '$dishId', 'x', 100, 'JPY', 1, NULL, NULL, NULL)"

    private fun exec(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    private fun bind(sql: String, placeId: String) = sql.replace(":placeId", "'$placeId'")

    private fun ids(sql: String): List<String> = db.createStatement().use { st ->
        st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
    }

    private fun deletePlace(placeId: String): List<String> {
        val paths = ids(bind(PLACE_PHOTO_PATHS_SQL, placeId))
        DELETE_PLACE_STATEMENTS.forEach { exec(bind(it, placeId)) }
        return paths
    }

    private fun assertOnlyToriLeft() {
        assertEquals(listOf("tori"), ids("SELECT id FROM place"))
        assertEquals(listOf("tori"), ids("SELECT placeId FROM place_tag"))
        assertEquals(listOf("s2"), ids("SELECT id FROM menu_scan"))
        assertEquals(listOf("d3"), ids("SELECT id FROM dish"))
        assertEquals(listOf("l2"), ids("SELECT id FROM dining_log"))
        assertEquals(listOf("i2"), ids("SELECT id FROM dining_log_item"))
        assertEquals(listOf("l2"), ids("SELECT logId FROM dining_log_person"))
        assertEquals(listOf("p2"), ids("SELECT id FROM photo"))
        // 纪念日、人和它们之间的关联都留着；别家店的记录还标着这个纪念日。
        assertEquals(listOf("love"), ids("SELECT id FROM milestone"))
        assertEquals(listOf("wife"), ids("SELECT id FROM person"))
        assertEquals(listOf("love"), ids("SELECT milestoneId FROM milestone_person"))
        assertEquals(listOf("love"), ids("SELECT milestoneId FROM dining_log WHERE id = 'l2'"))
    }

    @Test
    fun deletesEverythingOfThePlaceWithoutRelyingOnCascade() {
        open(foreignKeys = false)
        assertEquals(setOf("photos/s1.jpg", "photos/p1.jpg", "photos/r1.jpg"), deletePlace("minato").toSet())
        assertOnlyToriLeft()
    }

    @Test
    fun orderIsValidWithForeignKeysOn() {
        open(foreignKeys = true)
        deletePlace("minato")
        assertOnlyToriLeft()
        exec("PRAGMA foreign_key_check")
        assertEquals(emptyList(), ids("PRAGMA foreign_key_check"))
    }

    @Test
    fun unknownPlaceChangesNothing() {
        open(foreignKeys = true)
        assertEquals(emptyList(), deletePlace("nope"))
        assertEquals(listOf("minato", "tori"), ids("SELECT id FROM place ORDER BY id"))
        assertEquals(3, ids("SELECT id FROM photo").size)
    }
}
