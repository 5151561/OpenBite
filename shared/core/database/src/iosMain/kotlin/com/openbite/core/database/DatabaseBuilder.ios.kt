package com.openbite.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/** iOS：数据库放在 App 的 Documents 目录，用打包进来的 SQLite（BundledSQLiteDriver）。 */
@OptIn(ExperimentalForeignApi::class)
fun openBiteDatabaseBuilder(): RoomDatabase.Builder<OpenBiteDatabase> {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val dir = requireNotNull(documents?.path) { "Documents directory unavailable" }
    return Room.databaseBuilder<OpenBiteDatabase>(name = "$dir/$DATABASE_NAME")
        .setDriver(BundledSQLiteDriver())
}
