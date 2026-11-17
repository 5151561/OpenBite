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
import com.openbite.core.database.entity.PhotoEntity
import com.openbite.core.database.entity.PlaceEntity
import com.openbite.core.database.entity.PlaceTagEntity
import com.openbite.core.database.entity.PlaceWithTags
import kotlinx.coroutines.flow.Flow

/**
 * 搜索用的 SQL（LIKE，转义符是反斜杠；:pattern 由 likePattern 生成）。
 * 单独列成常量，好在 JVM 上直接跑（见 SearchSqlTest）。
 */
const val SEARCH_LOGS_SQL =
    "SELECT l.id FROM dining_log l " +
        "JOIN place p ON p.id = l.placeId " +
        "LEFT JOIN milestone m ON m.id = l.milestoneId " +
        "WHERE p.name LIKE :pattern ESCAPE '\\' OR l.note LIKE :pattern ESCAPE '\\' OR m.title LIKE :pattern ESCAPE '\\' " +
        "OR EXISTS (SELECT 1 FROM dining_log_item i WHERE i.logId = l.id AND (i.nameSnapshot LIKE :pattern ESCAPE '\\' " +
        "OR i.nameTranslatedSnapshot LIKE :pattern ESCAPE '\\' OR i.comment LIKE :pattern ESCAPE '\\')) " +
        "OR EXISTS (SELECT 1 FROM dining_log_person lp JOIN person pe ON pe.id = lp.personId " +
        "WHERE lp.logId = l.id AND pe.displayName LIKE :pattern ESCAPE '\\') " +
        "ORDER BY l.visitedAt DESC LIMIT :limit"

// 删除一家店用的 SQL，按子表到父表的顺序（JVM 测试按 [DELETE_PLACE_STATEMENTS] 原样跑）。
const val DELETE_PLACE_LOG_PHOTOS_SQL = "DELETE FROM photo WHERE logId IN (SELECT id FROM dining_log WHERE placeId = :placeId)"
const val DELETE_PLACE_LOG_ITEMS_SQL = "DELETE FROM dining_log_item WHERE logId IN (SELECT id FROM dining_log WHERE placeId = :placeId)"
const val DELETE_PLACE_LOG_PERSONS_SQL = "DELETE FROM dining_log_person WHERE logId IN (SELECT id FROM dining_log WHERE placeId = :placeId)"
const val DELETE_PLACE_LOGS_SQL = "DELETE FROM dining_log WHERE placeId = :placeId"
const val UNLINK_PLACE_DISHES_SQL = "UPDATE dining_log_item SET dishId = NULL WHERE dishId IN (SELECT id FROM dish WHERE placeId = :placeId)"
const val DELETE_PLACE_DISHES_SQL = "DELETE FROM dish WHERE placeId = :placeId"
const val DELETE_PLACE_SCANS_SQL = "DELETE FROM menu_scan WHERE placeId = :placeId"
const val DELETE_PLACE_TAGS_SQL = "DELETE FROM place_tag WHERE placeId = :placeId"
const val DELETE_PLACE_ROW_SQL = "DELETE FROM place WHERE id = :placeId"
const val PLACE_PHOTO_PATHS_SQL = "SELECT photoPath FROM menu_scan WHERE placeId = :placeId UNION ALL SELECT path FROM photo WHERE logId IN (SELECT id FROM dining_log WHERE placeId = :placeId)"
val DELETE_PLACE_STATEMENTS = listOf(
    DELETE_PLACE_LOG_PHOTOS_SQL,
    DELETE_PLACE_LOG_ITEMS_SQL,
    DELETE_PLACE_LOG_PERSONS_SQL,
    DELETE_PLACE_LOGS_SQL,
    UNLINK_PLACE_DISHES_SQL,
    DELETE_PLACE_DISHES_SQL,
    DELETE_PLACE_SCANS_SQL,
    DELETE_PLACE_TAGS_SQL,
    DELETE_PLACE_ROW_SQL,
)

const val SEARCH_PLACES_SQL =
    "SELECT DISTINCT p.id FROM place p " +
        "LEFT JOIN place_tag t ON t.placeId = p.id " +
        "LEFT JOIN dish d ON d.placeId = p.id " +
        "WHERE p.name LIKE :pattern ESCAPE '\\' OR p.address LIKE :pattern ESCAPE '\\' " +
        "OR t.tag LIKE :pattern ESCAPE '\\' OR d.nameOriginal LIKE :pattern ESCAPE '\\' " +
        "OR d.nameTranslated LIKE :pattern ESCAPE '\\' " +
        "LIMIT :limit"

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

    @Query("UPDATE place SET lat = :lat, lon = :lon WHERE id = :placeId")
    suspend fun setLocation(placeId: String, lat: Double, lon: Double)

    @Query(
        "UPDATE dish SET nameTranslated = :nameTranslated, category = :category, priceMinor = :priceMinor, currency = :currency " +
            "WHERE id = :dishId",
    )
    suspend fun updateDish(dishId: String, nameTranslated: String, category: String, priceMinor: Long?, currency: String?)

    /** 店铺搜索：店名、地址、标签、菜名（原文或译名）。[pattern] 已转义，用 \ 作转义符。 */
    @Query(SEARCH_PLACES_SQL)
    suspend fun searchPlaceIds(pattern: String, limit: Int): List<String>

    @Query("DELETE FROM place_tag WHERE placeId = :placeId AND tag = :tag")
    suspend fun deleteTag(placeId: String, tag: String)

    /** 所有店铺用过的标签，按用得多的在前（加标签时给建议）。 */
    @Query("SELECT tag FROM place_tag GROUP BY tag ORDER BY COUNT(*) DESC, tag")
    fun observeAllTags(): Flow<List<String>>

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

    /** 记忆搜索：店名、菜名（原文、译名）、菜的短评、这一餐的短评、同行人、纪念日标题。 */
    @Query(SEARCH_LOGS_SQL)
    suspend fun searchLogIds(pattern: String, limit: Int): List<String>

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

    // ---- 删除一家店 ----
    // 外键虽然都是 ON DELETE CASCADE，这里仍按子表到父表显式删，不依赖连接上有没有开 foreign_keys。

    @Query("SELECT COUNT(*) FROM dish WHERE placeId = :placeId") abstract suspend fun dishCountOf(placeId: String): Int
    @Query("SELECT COUNT(*) FROM dining_log WHERE placeId = :placeId") abstract suspend fun logCountOf(placeId: String): Int
    /** 菜单原照片 + 饭菜照片 + 小票照片的文件路径。 */
    @Query(PLACE_PHOTO_PATHS_SQL) abstract suspend fun photoPathsOf(placeId: String): List<String>
    @Query("SELECT id FROM dining_log WHERE placeId = :placeId") abstract suspend fun logIdsOf(placeId: String): List<String>
    @Query("SELECT name FROM place WHERE id = :placeId") abstract suspend fun placeName(placeId: String): String?

    @Query(DELETE_PLACE_LOG_PHOTOS_SQL)
    protected abstract suspend fun deleteLogPhotosOf(placeId: String)
    @Query(DELETE_PLACE_LOG_ITEMS_SQL)
    protected abstract suspend fun deleteLogItemsOf(placeId: String)
    @Query(DELETE_PLACE_LOG_PERSONS_SQL)
    protected abstract suspend fun deleteLogPersonsOf(placeId: String)
    @Query(DELETE_PLACE_LOGS_SQL) protected abstract suspend fun deleteLogsOf(placeId: String)
    /** 别家店的记录不会引用这家店的菜，万一有（旧数据）就断开，不删记录。 */
    @Query(UNLINK_PLACE_DISHES_SQL)
    protected abstract suspend fun unlinkDishesOf(placeId: String)
    @Query(DELETE_PLACE_DISHES_SQL) protected abstract suspend fun deleteDishesOf(placeId: String)
    @Query(DELETE_PLACE_SCANS_SQL) protected abstract suspend fun deleteScansOf(placeId: String)
    @Query(DELETE_PLACE_TAGS_SQL) protected abstract suspend fun deleteTagsOf(placeId: String)
    @Query(DELETE_PLACE_ROW_SQL) protected abstract suspend fun deletePlaceRow(placeId: String): Int

    /**
     * 删一家店：店铺、标签、菜单照片记录、菜品、这家店的用餐记录（菜、同行人关联、饭菜照片和小票照片）。
     * 纪念日和人不动（只是关联行随记录一起没了）。整批一个事务；返回要删的照片文件和被删的记录 id，店不存在返回 null。
     */
    @Transaction
    open suspend fun deletePlace(placeId: String): DeletedPlaceRows? {
        if (placeName(placeId) == null) return null
        val rows = DeletedPlaceRows(
            photoPaths = photoPathsOf(placeId),
            logIds = logIdsOf(placeId),
        )
        deleteLogPhotosOf(placeId)
        deleteLogItemsOf(placeId)
        deleteLogPersonsOf(placeId)
        deleteLogsOf(placeId)
        unlinkDishesOf(placeId)
        deleteDishesOf(placeId)
        deleteScansOf(placeId)
        deleteTagsOf(placeId)
        deletePlaceRow(placeId)
        return rows
    }
}

/** 删掉一家店后要在事务外处理的：照片文件（相对路径）和被删的用餐记录 id（取消饭后提醒用）。 */
data class DeletedPlaceRows(val photoPaths: List<String>, val logIds: List<String>)

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

/** 点完菜（“已下单”）生成的草稿记录，整批写入：新店铺（可选）、记录、菜品快照。 */
@Dao
abstract class DraftDao {
    @Insert
    protected abstract suspend fun insertPlace(place: PlaceEntity)

    @Insert
    protected abstract suspend fun insertLog(log: DiningLogEntity)

    @Insert
    protected abstract suspend fun insertItems(items: List<DiningLogItemEntity>)

    @Transaction
    open suspend fun insert(newPlace: PlaceEntity?, log: DiningLogEntity, items: List<DiningLogItemEntity>) {
        newPlace?.let { insertPlace(it) }
        insertLog(log)
        insertItems(items)
    }
}

/** 一餐的查看和编辑（补记忆、小票存档、饭后提醒）。 */
@Dao
abstract class MealDao {
    @Transaction
    @Query("SELECT * FROM dining_log WHERE id = :logId")
    abstract fun observe(logId: String): Flow<DiningLogWithDetails?>

    @Transaction
    @Query("SELECT * FROM dining_log WHERE id = :logId")
    abstract suspend fun byId(logId: String): DiningLogWithDetails?

    @Query("SELECT status FROM dining_log WHERE id = :logId")
    abstract suspend fun statusOf(logId: String): String?

    @Query("SELECT * FROM person ORDER BY displayName")
    abstract fun observePersons(): Flow<List<PersonEntity>>

    @Insert
    abstract suspend fun insertPerson(person: PersonEntity)

    @Query(
        "UPDATE dining_log SET rating = :rating, note = :note, milestoneId = :milestoneId, status = :status, " +
            "updatedAt = :updatedAt WHERE id = :logId",
    )
    protected abstract suspend fun updateLog(logId: String, rating: Int?, note: String?, milestoneId: String?, status: String, updatedAt: Long)

    @Query("UPDATE dining_log_item SET qty = :qty, rating = :rating, comment = :comment WHERE id = :itemId AND logId = :logId")
    protected abstract suspend fun updateItem(logId: String, itemId: String, qty: Int, rating: Int?, comment: String?)

    @Query("DELETE FROM dining_log_item WHERE logId = :logId AND id IN (:itemIds)")
    protected abstract suspend fun deleteItems(logId: String, itemIds: List<String>)

    @Query("DELETE FROM dining_log_person WHERE logId = :logId")
    protected abstract suspend fun clearPersons(logId: String)

    @Insert
    protected abstract suspend fun insertPersonLinks(links: List<DiningLogPersonEntity>)

    @Insert
    protected abstract suspend fun insertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM photo WHERE logId = :logId AND id IN (:photoIds)")
    protected abstract suspend fun deletePhotos(logId: String, photoIds: List<String>)

    @Query("SELECT path FROM photo WHERE logId = :logId AND kind = 'receipt'")
    abstract suspend fun receiptPhotoPaths(logId: String): List<String>

    @Query("DELETE FROM photo WHERE logId = :logId AND kind = 'receipt'")
    protected abstract suspend fun deleteReceiptPhotos(logId: String)

    @Query("UPDATE dining_log SET totalMinor = :totalMinor, currency = :currency, updatedAt = :updatedAt WHERE id = :logId")
    protected abstract suspend fun updateTotal(logId: String, totalMinor: Long?, currency: String?, updatedAt: Long)

    /** 补记忆的保存：一条记录的评分、短评、纪念日、状态、菜品、同行人、照片，一个事务。 */
    @Transaction
    open suspend fun saveEdit(
        logId: String,
        rating: Int?,
        note: String?,
        milestoneId: String?,
        status: String,
        updatedAt: Long,
        items: List<DiningLogItemEntity>,
        removedItemIds: List<String>,
        personIds: List<String>,
        addedPhotos: List<PhotoEntity>,
        removedPhotoIds: List<String>,
    ) {
        updateLog(logId, rating, note, milestoneId, status, updatedAt)
        items.forEach { updateItem(logId, it.id, it.qty, it.rating, it.comment) }
        if (removedItemIds.isNotEmpty()) deleteItems(logId, removedItemIds)
        clearPersons(logId)
        if (personIds.isNotEmpty()) insertPersonLinks(personIds.distinct().map { DiningLogPersonEntity(logId, it) })
        if (addedPhotos.isNotEmpty()) insertPhotos(addedPhotos)
        if (removedPhotoIds.isNotEmpty()) deletePhotos(logId, removedPhotoIds)
    }

    /** 小票存档：换掉旧的小票照片（[photo] 为 null 表示只记金额、不留照片），写实付金额。 */
    @Transaction
    open suspend fun saveReceipt(logId: String, photo: PhotoEntity?, totalMinor: Long?, currency: String?, updatedAt: Long) {
        if (photo != null) {
            deleteReceiptPhotos(logId)
            insertPhotos(listOf(photo))
        }
        updateTotal(logId, totalMinor, currency, updatedAt)
    }
}

/** 导入店铺文件：新店铺、标签、菜单原照片、菜品，一个事务。 */
@Dao
abstract class ImportDao {
    @Insert
    protected abstract suspend fun insertPlace(place: PlaceEntity)

    @Insert
    protected abstract suspend fun insertTags(tags: List<PlaceTagEntity>)

    @Insert
    protected abstract suspend fun insertScans(scans: List<MenuScanEntity>)

    @Insert
    protected abstract suspend fun insertDishes(dishes: List<DishEntity>)

    @Transaction
    open suspend fun insert(place: PlaceEntity, tags: List<PlaceTagEntity>, scans: List<MenuScanEntity>, dishes: List<DishEntity>) {
        insertPlace(place)
        if (tags.isNotEmpty()) insertTags(tags)
        if (scans.isNotEmpty()) insertScans(scans)
        if (dishes.isNotEmpty()) insertDishes(dishes)
    }
}

/** 纪念日管理：读、存（含“关于谁”）、删。 */
@Dao
abstract class MilestoneEditDao {
    @Query("SELECT * FROM milestone WHERE id = :id")
    abstract suspend fun byId(id: String): MilestoneEntity?

    @Query("SELECT personId FROM milestone_person WHERE milestoneId = :id")
    abstract suspend fun personIdsOf(id: String): List<String>

    @Query("SELECT COUNT(*) FROM dining_log WHERE milestoneId = :id")
    abstract suspend fun taggedLogCount(id: String): Int

    @Upsert
    protected abstract suspend fun upsert(milestone: MilestoneEntity)

    @Query("DELETE FROM milestone_person WHERE milestoneId = :id")
    protected abstract suspend fun clearLinks(id: String)

    @Insert
    protected abstract suspend fun insertLinks(links: List<MilestonePersonEntity>)

    @Query("UPDATE dining_log SET milestoneId = NULL WHERE milestoneId = :id")
    protected abstract suspend fun untagLogs(id: String)

    @Query("DELETE FROM milestone WHERE id = :id")
    protected abstract suspend fun deleteById(id: String)

    @Transaction
    open suspend fun save(milestone: MilestoneEntity, personIds: List<String>) {
        upsert(milestone)
        clearLinks(milestone.id)
        if (personIds.isNotEmpty()) insertLinks(personIds.distinct().map { MilestonePersonEntity(milestone.id, it) })
    }

    /** 外键本来就是 SET NULL；这里显式先改，dining_log 的观察者一定会收到更新。 */
    @Transaction
    open suspend fun delete(id: String) {
        untagLogs(id)
        deleteById(id)
    }
}
