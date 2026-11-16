package com.openbite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.openbite.core.database.entity.DiningLogEntity
import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.DiningLogPersonEntity
import com.openbite.core.database.entity.DiningLogWithDetails
import com.openbite.core.database.entity.DishEntity
import com.openbite.core.database.entity.MilestoneEntity
import com.openbite.core.database.entity.MenuScanEntity
import com.openbite.core.database.entity.MilestonePersonEntity
import com.openbite.core.database.entity.PlaceCount
import com.openbite.core.database.entity.PersonEntity
import com.openbite.core.database.entity.PlaceEntity
import com.openbite.core.database.entity.PlaceTagEntity
import com.openbite.core.database.entity.PlaceWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Upsert
    suspend fun upsertTags(tags: List<PlaceTagEntity>)

    @Upsert
    suspend fun upsertDishes(dishes: List<DishEntity>)

    @Query("SELECT COUNT(*) FROM place")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM place ORDER BY name")
    fun observeAll(): Flow<List<PlaceWithTags>>

    @Query("SELECT * FROM dish WHERE placeId = :placeId ORDER BY category, nameOriginal")
    suspend fun dishesOf(placeId: String): List<DishEntity>

    @Transaction
    @Query("SELECT * FROM place WHERE id = :placeId")
    fun observeById(placeId: String): Flow<PlaceWithTags?>

    /** 按写入顺序（rowid）返回，保持菜单上的原顺序。 */
    @Query("SELECT * FROM dish WHERE placeId = :placeId ORDER BY rowid")
    fun observeDishesOf(placeId: String): Flow<List<DishEntity>>

    @Query("SELECT * FROM menu_scan WHERE placeId = :placeId ORDER BY createdAt, rowid")
    fun observeScansOf(placeId: String): Flow<List<MenuScanEntity>>

    @Transaction
    @Query("SELECT * FROM place WHERE id = :placeId")
    suspend fun byId(placeId: String): PlaceWithTags?

    @Query("SELECT * FROM dish WHERE placeId = :placeId ORDER BY rowid")
    suspend fun menuOf(placeId: String): List<DishEntity>

    @Query("UPDATE place SET name = :name WHERE id = :placeId")
    suspend fun rename(placeId: String, name: String)

    @Query("SELECT placeId, COUNT(*) AS count FROM dish GROUP BY placeId")
    fun observeDishCounts(): Flow<List<PlaceCount>>

    @Query("SELECT placeId, COUNT(*) AS count FROM menu_scan GROUP BY placeId")
    fun observeScanCounts(): Flow<List<PlaceCount>>
}

@Dao
interface DiningLogDao {
    @Upsert
    suspend fun upsert(log: DiningLogEntity)

    @Upsert
    suspend fun upsertItems(items: List<DiningLogItemEntity>)

    @Upsert
    suspend fun upsertPersons(links: List<DiningLogPersonEntity>)

    @Transaction
    @Query("SELECT * FROM dining_log ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<DiningLogWithDetails>>

    @Transaction
    @Query("SELECT * FROM dining_log WHERE milestoneId = :milestoneId ORDER BY visitedAt DESC")
    suspend fun byMilestone(milestoneId: String): List<DiningLogWithDetails>
}

@Dao
interface MilestoneDao {
    @Upsert
    suspend fun upsert(milestone: MilestoneEntity)

    @Upsert
    suspend fun upsertPersons(persons: List<PersonEntity>)

    @Upsert
    suspend fun upsertLinks(links: List<MilestonePersonEntity>)

    @Query("SELECT * FROM milestone")
    fun observeAll(): Flow<List<MilestoneEntity>>
}

/** 首次启动导入种子数据，整批在一个事务里写入。 */
@Dao
abstract class SeedDao {
    @Insert
    protected abstract suspend fun insertPersons(items: List<PersonEntity>)

    @Insert
    protected abstract suspend fun insertMilestones(items: List<MilestoneEntity>)

    @Insert
    protected abstract suspend fun insertMilestonePersons(items: List<MilestonePersonEntity>)

    @Insert
    protected abstract suspend fun insertPlaces(items: List<PlaceEntity>)

    @Insert
    protected abstract suspend fun insertPlaceTags(items: List<PlaceTagEntity>)

    @Insert
    protected abstract suspend fun insertMenuScans(items: List<MenuScanEntity>)

    @Insert
    protected abstract suspend fun insertDishes(items: List<DishEntity>)

    @Insert
    protected abstract suspend fun insertLogs(items: List<DiningLogEntity>)

    @Insert
    protected abstract suspend fun insertLogItems(items: List<DiningLogItemEntity>)

    @Insert
    protected abstract suspend fun insertLogPersons(items: List<DiningLogPersonEntity>)

    @Transaction
    open suspend fun insertAll(
        persons: List<PersonEntity>,
        milestones: List<MilestoneEntity>,
        milestonePersons: List<MilestonePersonEntity>,
        places: List<PlaceEntity>,
        placeTags: List<PlaceTagEntity>,
        menuScans: List<MenuScanEntity>,
        dishes: List<DishEntity>,
        logs: List<DiningLogEntity>,
        logItems: List<DiningLogItemEntity>,
        logPersons: List<DiningLogPersonEntity>,
    ) {
        insertPersons(persons)
        insertMilestones(milestones)
        insertMilestonePersons(milestonePersons)
        insertPlaces(places)
        insertPlaceTags(placeTags)
        insertMenuScans(menuScans)
        insertDishes(dishes)
        insertLogs(logs)
        insertLogItems(logItems)
        insertLogPersons(logPersons)
    }
}

/** 设置页的“清空全部记忆”。按外键从子表删到父表，整批在一个事务里。 */
@Dao
abstract class MaintenanceDao {
    @Query("DELETE FROM dining_log_item") protected abstract suspend fun deleteLogItems()
    @Query("DELETE FROM dining_log_person") protected abstract suspend fun deleteLogPersons()
    @Query("DELETE FROM photo") protected abstract suspend fun deletePhotos()
    @Query("DELETE FROM dining_log") protected abstract suspend fun deleteLogs()
    @Query("DELETE FROM dish") protected abstract suspend fun deleteDishes()
    @Query("DELETE FROM menu_scan") protected abstract suspend fun deleteMenuScans()
    @Query("DELETE FROM place_tag") protected abstract suspend fun deletePlaceTags()
    @Query("DELETE FROM place") protected abstract suspend fun deletePlaces()
    @Query("DELETE FROM milestone_person") protected abstract suspend fun deleteMilestonePersons()
    @Query("DELETE FROM milestone") protected abstract suspend fun deleteMilestones()
    @Query("DELETE FROM person") protected abstract suspend fun deletePersons()

    @Transaction
    open suspend fun clearAll() {
        deleteLogItems()
        deleteLogPersons()
        deletePhotos()
        deleteLogs()
        deleteDishes()
        deleteMenuScans()
        deletePlaceTags()
        deletePlaces()
        deleteMilestonePersons()
        deleteMilestones()
        deletePersons()
    }
}

/** 一次识别的结果整批写入：新店铺（可选）、menu_scan、合并后的菜品。 */
@Dao
abstract class ScanDao {
    @Insert
    protected abstract suspend fun insertPlace(place: PlaceEntity)

    @Insert
    protected abstract suspend fun insertScan(scan: MenuScanEntity)

    /** Upsert 对已有的行做 UPDATE，rowid 不变，所以菜单顺序保持；新菜排到后面。 */
    @Upsert
    protected abstract suspend fun upsertDishes(dishes: List<DishEntity>)

    @Transaction
    open suspend fun save(newPlace: PlaceEntity?, scan: MenuScanEntity, dishes: List<DishEntity>) {
        newPlace?.let { insertPlace(it) }
        insertScan(scan)
        upsertDishes(dishes)
    }
}
