package com.openbite.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.openbite.core.database.dao.DiningLogDao
import com.openbite.core.database.dao.DraftDao
import com.openbite.core.database.dao.MealDao
import com.openbite.core.database.dao.MaintenanceDao
import com.openbite.core.database.dao.MilestoneDao
import com.openbite.core.database.dao.PlaceDao
import com.openbite.core.database.dao.ScanDao
import com.openbite.core.database.dao.SeedDao
import com.openbite.core.database.entity.DiningLogEntity
import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.DiningLogPersonEntity
import com.openbite.core.database.entity.DishEntity
import com.openbite.core.database.entity.MenuScanEntity
import com.openbite.core.database.entity.MilestoneEntity
import com.openbite.core.database.entity.MilestonePersonEntity
import com.openbite.core.database.entity.PersonEntity
import com.openbite.core.database.entity.PhotoEntity
import com.openbite.core.database.entity.PlaceEntity
import com.openbite.core.database.entity.PlaceTagEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

const val DATABASE_NAME = "openbite.db"

@Database(
    entities = [
        PlaceEntity::class,
        PlaceTagEntity::class,
        MenuScanEntity::class,
        DishEntity::class,
        PersonEntity::class,
        MilestoneEntity::class,
        MilestonePersonEntity::class,
        DiningLogEntity::class,
        DiningLogItemEntity::class,
        DiningLogPersonEntity::class,
        PhotoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(OpenBiteDatabaseConstructor::class)
abstract class OpenBiteDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun diningLogDao(): DiningLogDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun seedDao(): SeedDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun scanDao(): ScanDao
    abstract fun draftDao(): DraftDao
    abstract fun mealDao(): MealDao
}

// Room 编译器为每个平台生成 actual 实现。
@Suppress("KotlinNoActualForExpect")
expect object OpenBiteDatabaseConstructor : RoomDatabaseConstructor<OpenBiteDatabase> {
    override fun initialize(): OpenBiteDatabase
}

/** 两端共用的收尾配置；平台只负责给出 Builder（文件路径、驱动）。 */
fun RoomDatabase.Builder<OpenBiteDatabase>.buildOpenBite(): OpenBiteDatabase =
    addMigrations(MIGRATION_1_2)
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
