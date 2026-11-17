package com.openbite.core.database

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
import kotlin.test.assertTrue

/**
 * 在 JVM 上用 SQLite（sqlite-jdbc）验证 v1 -> v2 迁移：
 * 按导出的 schemas/1.json 建 v1 表、塞数据，跑 [MIGRATION_1_2_SQL]，
 * 再检查数据搬对了、表结构和 Room 导出的 schemas/2.json 一致（列名、类型、非空、默认值）。
 * 没用 Room 的 MigrationTestHelper：它要 Android 运行时或 JVM 版 bundled 驱动，本机缓存里都没有。
 */
class MigrationSqlTest {
    private lateinit var db: Connection
    private val schemaDir = File(System.getProperty("user.dir"), "schemas/com.openbite.core.database.OpenBiteDatabase")

    @BeforeTest
    fun setUp() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        createSqls(1).forEach(::exec)
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun exec(sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    private fun createSqls(version: Int): List<String> {
        val json = Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject
        val database = json.getValue("database").jsonObject
        return database.getValue("entities").jsonArray.flatMap { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            val create = entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            val indices = entity.jsonObject["indices"]?.jsonArray.orEmpty().map {
                it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            }
            listOf(create) + indices
        }
    }

    /** 列名 -> (类型, 非空, 默认值)，来自 SQLite 自己的 PRAGMA。 */
    private fun columns(table: String): Map<String, Triple<String, Boolean, String?>> =
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
                buildMap {
                    while (rs.next()) put(rs.getString("name"), Triple(rs.getString("type"), rs.getInt("notnull") == 1, rs.getString("dflt_value")))
                }
            }
        }

    private fun query(sql: String): List<List<String?>> = db.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            buildList {
                while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) })
            }
        }
    }

    private fun seedV1() {
        exec("INSERT INTO place (id, name, lat, lon, address, createdAt) VALUES ('p', '割烹 みなと', NULL, NULL, NULL, 0)")
        exec(
            "INSERT INTO dish (id, placeId, scanId, category, nameOriginal, nameTranslated, priceMinor, currency, allergensJson) " +
                "VALUES ('d-uni', 'p', NULL, '蒸物', 'うに茶碗蒸し', '海胆茶碗蒸', 2800, 'JPY', '[]')",
        )
        exec(
            "INSERT INTO dining_log (id, placeId, visitedAt, timezone, localDate, status, milestoneId, note, createdBy, createdAt, updatedAt) " +
                "VALUES ('l', 'p', 0, 'Asia/Tokyo', '2025-11-27', 'done', NULL, NULL, 'user', 0, 0)",
        )
        // 关联到菜品的：v1 存的是译名。
        exec(
            "INSERT INTO dining_log_item (id, logId, dishId, nameSnapshot, priceMinorSnapshot, currencySnapshot, qty, rating, comment) " +
                "VALUES ('i1', 'l', 'd-uni', '海胆茶碗蒸', 2800, 'JPY', 1, 5, NULL)",
        )
        // 没关联的：保留原样。
        exec(
            "INSERT INTO dining_log_item (id, logId, dishId, nameSnapshot, priceMinorSnapshot, currencySnapshot, qty, rating, comment) " +
                "VALUES ('i2', 'l', NULL, '本日のおすすめ', 1200, 'JPY', 1, NULL, NULL)",
        )
        exec("INSERT INTO photo (id, logId, path, takenAt) VALUES ('ph', 'l', 'photos/ph.jpg', 0)")
    }

    @Test
    fun migratesItemNamesAndAddsColumns() {
        seedV1()
        MIGRATION_1_2_SQL.forEach(::exec)

        assertEquals(
            listOf(listOf("i1", "うに茶碗蒸し", "海胆茶碗蒸"), listOf("i2", "本日のおすすめ", "本日のおすすめ")),
            query("SELECT id, nameSnapshot, nameTranslatedSnapshot FROM dining_log_item ORDER BY id"),
        )
        assertEquals(listOf(listOf<String?>(null, null, null)), query("SELECT rating, totalMinor, currency FROM dining_log"))
        assertEquals(listOf(listOf<String?>("meal")), query("SELECT kind FROM photo"))
    }

    private fun tables(): List<String> =
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name").map { it.single()!! }

    /** 索引名 -> (唯一, 列)。 */
    private fun indices(table: String): Map<String, Pair<Boolean, List<String>>> =
        query("PRAGMA index_list(`$table`)").filter { it[1]?.startsWith("sqlite_autoindex") == false }.associate { row ->
            val name = row[1]!!
            name to ((row[2] == "1") to query("PRAGMA index_info(`$name`)").map { it[2]!! })
        }

    /** 外键：(引用的表, 本表列, 对方列, ON UPDATE, ON DELETE)。 */
    private fun foreignKeys(table: String): Set<List<String?>> =
        query("PRAGMA foreign_key_list(`$table`)").map { listOf(it[2], it[3], it[4], it[5], it[6]) }.toSet()

    private data class Schema(
        val columns: Map<String, Map<String, Triple<String, Boolean, String?>>>,
        val indices: Map<String, Map<String, Pair<Boolean, List<String>>>>,
        val foreignKeys: Map<String, Set<List<String?>>>,
    )

    private fun schema(): Schema {
        val all = tables()
        return Schema(all.associateWith(::columns), all.associateWith(::indices), all.associateWith(::foreignKeys))
    }

    @Test
    fun migratedSchemaMatchesExportedV2() {
        MIGRATION_1_2_SQL.forEach(::exec)
        val migrated = schema()

        db.close()
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        createSqls(2).forEach(::exec)
        val fresh = schema()

        // 全部表都比：列（类型、非空、默认值）、索引（唯一、列顺序）、外键（引用、级联）。
        assertEquals(fresh.columns.keys, migrated.columns.keys)
        assertEquals(fresh.columns, migrated.columns)
        assertEquals(fresh.indices, migrated.indices)
        assertEquals(fresh.foreignKeys, migrated.foreignKeys)
    }

    @Test
    fun schemaCheckSeesIndicesAndForeignKeys() {
        // 确认比较真的读到了东西：v2 里有索引和外键，空比较会让上面的测试形同虚设。
        createSqls(2).forEach(::exec)
        val schema = schema()
        assertTrue(schema.indices.values.sumOf { it.size } > 0)
        assertTrue(schema.foreignKeys.values.sumOf { it.size } > 0)
    }
}
