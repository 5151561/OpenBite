package com.openbite.core.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v1 -> v2：
 * - dining_log 加 rating（这一餐的总评分）、totalMinor + currency（小票实付）；
 * - dining_log_item 加 nameTranslatedSnapshot，nameSnapshot 改成菜单原文：
 *   关联到菜品的，原文取 dish.nameOriginal、旧值（译名）挪到 nameTranslatedSnapshot；
 *   没关联的保留 nameSnapshot，并复制一份到 nameTranslatedSnapshot；
 * - photo 加 kind（meal | receipt），旧照片都算 meal。
 *
 * SQL 单独列出来，方便在 JVM 上用 SQLite 直接测（见 MigrationSqlTest）。
 */
val MIGRATION_1_2_SQL: List<String> = listOf(
    "ALTER TABLE `dining_log` ADD COLUMN `rating` INTEGER",
    "ALTER TABLE `dining_log` ADD COLUMN `totalMinor` INTEGER",
    "ALTER TABLE `dining_log` ADD COLUMN `currency` TEXT",
    "ALTER TABLE `dining_log_item` ADD COLUMN `nameTranslatedSnapshot` TEXT",
    "UPDATE `dining_log_item` SET `nameTranslatedSnapshot` = `nameSnapshot`",
    """
    UPDATE `dining_log_item`
    SET `nameSnapshot` = (SELECT `dish`.`nameOriginal` FROM `dish` WHERE `dish`.`id` = `dining_log_item`.`dishId`)
    WHERE `dishId` IS NOT NULL AND EXISTS (SELECT 1 FROM `dish` WHERE `dish`.`id` = `dining_log_item`.`dishId`)
    """.trimIndent(),
    "ALTER TABLE `photo` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'meal'",
)

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        MIGRATION_1_2_SQL.forEach { connection.execSQL(it) }
    }
}

/**
 * v2 -> v3：place 加 countryCode（店铺位置所在的国家/地区，ISO 3166 两位字母），
 * 用来推默认币种。老数据为 NULL，用到时按经纬度再反查。
 */
val MIGRATION_2_3_SQL: List<String> = listOf(
    "ALTER TABLE `place` ADD COLUMN `countryCode` TEXT",
)

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        MIGRATION_2_3_SQL.forEach { connection.execSQL(it) }
    }
}
