package com.openbite.core.data

import com.openbite.core.model.DiningStatus
import com.openbite.core.model.MilestoneWithPeople
import com.openbite.core.model.NewPhoto
import com.openbite.core.model.MealEdit
import com.openbite.core.data.seed.SeedImporter
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.database.entity.DiningLogEntity
import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.MenuScanEntity
import com.openbite.core.database.entity.PersonEntity
import com.openbite.core.database.entity.PhotoEntity
import com.openbite.core.model.DiningLog
import com.openbite.core.model.Dish
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Money
import com.openbite.core.model.Person
import com.openbite.core.model.PhotoKind
import com.openbite.core.model.Milestone
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface DiningLogRepository {
    fun observeLogs(): Flow<List<DiningLog>>

    /** 按 LIKE 模式（已转义，转义符是反斜杠）搜记录 id，最新的在前。 */
    suspend fun searchIds(pattern: String, limit: Int): List<String>
    suspend fun logsOfMilestone(milestoneId: String): List<DiningLog>
}

interface PlaceRepository {
    fun observePlaces(): Flow<List<Place>>
    fun observePlace(placeId: String): Flow<Place?>

    /** 电子菜单，按菜单上的原顺序。 */
    fun observeMenu(placeId: String): Flow<List<Dish>>

    /** 菜单原照片，按拍摄时间。 */
    fun observeScans(placeId: String): Flow<List<MenuScan>>

    /** 每家店的菜品数和原照片数。 */
    fun observeCounts(): Flow<Map<String, PlaceCounts>>

    suspend fun rename(placeId: String, name: String)

    /** 改位置；原来反查的国家/地区清掉。 */
    suspend fun setLocation(placeId: String, lat: Double, lon: Double)

    /** 存反查到的国家/地区。位置在这期间又变了（[lat]/[lon] 对不上）就不写。 */
    suspend fun setCountry(placeId: String, lat: Double, lon: Double, countryCode: String)

    /** 改一道菜的译名、分类、价格（用餐记录里的快照不变）。 */
    suspend fun updateDish(dishId: String, nameTranslated: String, category: String, price: Money?)

    /** 按 LIKE 模式（已转义）搜店铺 id。 */
    suspend fun searchIds(pattern: String, limit: Int): List<String>
    suspend fun addTag(placeId: String, tag: String)
    suspend fun removeTag(placeId: String, tag: String)

    /** 所有用过的标签，常用的在前。 */
    fun observeAllTags(): Flow<List<String>>
}

/** 一次识别要写进 menu_scan 的内容。 */
data class NewMenuScan(
    val id: String,
    val placeId: String,
    /** 相对路径 photos/<id>.jpg。 */
    val photoPath: String,
    /** 模型 id（Anthropic 的模型名或 "sample"）。 */
    val model: String,
    val sourceLang: String?,
    val targetLang: String,
    /** 模型的原始输出。 */
    val rawOutput: String,
    val createdAt: Long,
)

interface MenuScanRepository {
    suspend fun place(placeId: String): Place?

    /** 这家店现有的菜单，按菜单顺序。 */
    suspend fun menuOf(placeId: String): List<Dish>

    /** 一个事务：[newPlace] 不为 null 时先建店，再写 menu_scan，再 upsert 合并后的菜品。 */
    suspend fun save(newPlace: Place?, scan: NewMenuScan, dishes: List<Dish>)
}

internal class RoomMenuScanRepository(private val db: OpenBiteDatabase) : MenuScanRepository {
    override suspend fun place(placeId: String) = db.placeDao().byId(placeId)?.toModel()

    override suspend fun menuOf(placeId: String) = db.placeDao().menuOf(placeId).map { it.toModel() }

    override suspend fun save(newPlace: Place?, scan: NewMenuScan, dishes: List<Dish>) {
        db.scanDao().save(
            newPlace = newPlace?.toEntity(scan.createdAt),
            scan = MenuScanEntity(
                id = scan.id,
                placeId = scan.placeId,
                photoPath = scan.photoPath,
                model = scan.model,
                sourceLang = scan.sourceLang,
                targetLang = scan.targetLang,
                rawJson = scan.rawOutput,
                createdAt = scan.createdAt,
            ),
            dishes = dishes.map { it.toEntity() },
        )
    }
}

data class PlaceCounts(val dishes: Int, val photos: Int)

interface MilestoneRepository {
    fun observeMilestones(): Flow<List<Milestone>>

    /** 一个纪念日和“关于谁”；不在了返回 null。 */
    suspend fun milestone(id: String): MilestoneWithPeople?

    /** 新建或更新（同一个 id 就是更新），同时换掉“关于谁”。 */
    suspend fun save(milestone: Milestone, personIds: List<String>)

    /** 删掉纪念日；标了它的记录还在，只是去掉标签。 */
    suspend fun delete(id: String)

    /** 标了这个纪念日的用餐记录数。 */
    suspend fun taggedMealCount(id: String): Int
}

/** 设置页的示例数据和清空操作。 */
interface SampleDataRepository {
    /** 数据库为空时导入示例数据；返回是否真的导入了。 */
    suspend fun loadSampleIfEmpty(): Boolean

    /** 删除全部店铺、菜单、记录、纪念日和同行人。 */
    suspend fun clearAll()
}

internal class RoomSampleDataRepository(
    private val db: OpenBiteDatabase,
    private val importer: SeedImporter,
) : SampleDataRepository {
    override suspend fun loadSampleIfEmpty() = importer.importIfEmpty()

    override suspend fun clearAll() = db.maintenanceDao().clearAll()
}

internal class RoomDiningLogRepository(private val db: OpenBiteDatabase) : DiningLogRepository {
    override fun observeLogs() = db.diningLogDao().observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun logsOfMilestone(milestoneId: String) =
        db.diningLogDao().byMilestone(milestoneId).map { it.toModel() }

    override suspend fun searchIds(pattern: String, limit: Int) = db.diningLogDao().searchLogIds(pattern, limit)
}

internal class RoomPlaceRepository(private val db: OpenBiteDatabase) : PlaceRepository {
    override fun observePlaces() = db.placeDao().observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observePlace(placeId: String) = db.placeDao().observeById(placeId).map { it?.toModel() }

    override fun observeMenu(placeId: String) =
        db.placeDao().observeDishesOf(placeId).map { rows -> rows.map { it.toModel() } }

    override fun observeScans(placeId: String) =
        db.placeDao().observeScansOf(placeId).map { rows -> rows.map { it.toModel() } }

    override fun observeCounts(): Flow<Map<String, PlaceCounts>> =
        combine(db.placeDao().observeDishCounts(), db.placeDao().observeScanCounts()) { dishes, scans ->
            val dishMap = dishes.associate { it.placeId to it.count }
            val scanMap = scans.associate { it.placeId to it.count }
            (dishMap.keys + scanMap.keys).associateWith { PlaceCounts(dishMap[it] ?: 0, scanMap[it] ?: 0) }
        }

    override suspend fun rename(placeId: String, name: String) = db.placeDao().rename(placeId, name)

    override suspend fun setLocation(placeId: String, lat: Double, lon: Double) = db.placeDao().setLocation(placeId, lat, lon)

    override suspend fun setCountry(placeId: String, lat: Double, lon: Double, countryCode: String) =
        db.placeDao().setCountry(placeId, lat, lon, countryCode)

    override suspend fun updateDish(dishId: String, nameTranslated: String, category: String, price: Money?) =
        db.placeDao().updateDish(dishId, nameTranslated, category, price?.amountMinor, price?.currency)

    override suspend fun searchIds(pattern: String, limit: Int) = db.placeDao().searchPlaceIds(pattern, limit)

    override suspend fun addTag(placeId: String, tag: String) =
        db.placeDao().upsertTags(listOf(com.openbite.core.database.entity.PlaceTagEntity(placeId, tag)))

    override suspend fun removeTag(placeId: String, tag: String) = db.placeDao().deleteTag(placeId, tag)

    override fun observeAllTags(): Flow<List<String>> = db.placeDao().observeAllTags()
}

internal class RoomMilestoneRepository(private val db: OpenBiteDatabase) : MilestoneRepository {
    override fun observeMilestones() = db.milestoneDao().observeAll().map { rows -> rows.map { it.toModel() } }

    override suspend fun milestone(id: String): MilestoneWithPeople? {
        val dao = db.milestoneEditDao()
        val entity = dao.byId(id) ?: return null
        return MilestoneWithPeople(entity.toModel(), dao.personIdsOf(id))
    }

    override suspend fun save(milestone: Milestone, personIds: List<String>) =
        db.milestoneEditDao().save(milestone.toEntity(), personIds)

    override suspend fun delete(id: String) = db.milestoneEditDao().delete(id)

    override suspend fun taggedMealCount(id: String) = db.milestoneEditDao().taggedLogCount(id)
}

/** 草稿记录里的一道菜（名字和价格是快照）。 */
data class NewDraftItem(
    val id: String,
    /** 没关联到菜单时为 null。 */
    val dishId: String?,
    /** 菜单原文。 */
    val name: String,
    val nameTranslated: String?,
    val price: Money?,
    val qty: Int,
)

data class NewDraftLog(
    val id: String,
    val placeId: String,
    val visitedAt: Long,
    val timezone: String,
    /** 当地日期 "2026-11-17"。 */
    val localDate: String,
    val items: List<NewDraftItem>,
    val createdAt: Long,
    val milestoneId: String? = null,
    val note: String? = null,
    /** 手机助手（AppFunctions）写的记录为 true，界面上标“助手记录”。 */
    val byAssistant: Boolean = false,
)

interface DraftRepository {
    /** 一个事务：[newPlace] 不为 null 时先建店，再写草稿记录和菜品。 */
    suspend fun createDraft(newPlace: Place?, log: NewDraftLog)
}

internal class RoomDraftRepository(private val db: OpenBiteDatabase) : DraftRepository {
    override suspend fun createDraft(newPlace: Place?, log: NewDraftLog) {
        db.draftDao().insert(
            newPlace = newPlace?.toEntity(log.createdAt),
            log = DiningLogEntity(
                id = log.id,
                placeId = log.placeId,
                visitedAt = log.visitedAt,
                timezone = log.timezone,
                localDate = log.localDate,
                status = DiningStatus.DRAFT.toColumn(),
                milestoneId = log.milestoneId,
                note = log.note,
                createdBy = if (log.byAssistant) "assistant" else "user",
                createdAt = log.createdAt,
                updatedAt = log.createdAt,
            ),
            items = log.items.map {
                DiningLogItemEntity(
                    id = it.id,
                    logId = log.id,
                    dishId = it.dishId,
                    nameSnapshot = it.name,
                    priceMinorSnapshot = it.price?.amountMinor,
                    currencySnapshot = it.price?.currency,
                    qty = it.qty,
                    rating = null,
                    comment = null,
                    nameTranslatedSnapshot = it.nameTranslated,
                )
            },
        )
    }
}

interface MealRepository {
    fun observeMeal(logId: String): Flow<DiningLog?>
    suspend fun meal(logId: String): DiningLog?

    /** 记录不在了返回 null。 */
    suspend fun statusOf(logId: String): DiningStatus?
    fun observePersons(): Flow<List<Person>>
    suspend fun addPerson(person: Person)
    suspend fun save(edit: MealEdit)

    /** 现有的小票照片路径（换小票时删旧文件用）。 */
    suspend fun receiptPhotoPaths(logId: String): List<String>
    suspend fun saveReceipt(logId: String, photo: NewPhoto?, total: Money?, updatedAt: Long)
}

internal class RoomMealRepository(private val db: OpenBiteDatabase) : MealRepository {
    private val dao get() = db.mealDao()

    override fun observeMeal(logId: String) = dao.observe(logId).map { it?.toModel() }

    override suspend fun meal(logId: String) = dao.byId(logId)?.toModel()

    override suspend fun statusOf(logId: String) = dao.statusOf(logId)?.let(::diningStatusOf)

    override fun observePersons() = dao.observePersons().map { rows -> rows.map { Person(it.id, it.displayName) } }

    override suspend fun addPerson(person: Person) = dao.insertPerson(PersonEntity(person.id, person.displayName))

    override suspend fun save(edit: MealEdit) = dao.saveEdit(
        logId = edit.logId,
        rating = edit.rating,
        note = edit.note,
        milestoneId = edit.milestoneId,
        status = (if (edit.done) DiningStatus.DONE else DiningStatus.DRAFT).toColumn(),
        updatedAt = edit.updatedAt,
        // 只用到 id / qty / rating / comment，其余字段 DAO 不写。
        items = edit.items.map { DiningLogItemEntity(it.id, edit.logId, null, "", null, null, it.qty, it.rating, it.comment) },
        removedItemIds = edit.removedItemIds,
        personIds = edit.personIds,
        addedPhotos = edit.addedPhotos.map { it.toEntity(edit.logId) },
        removedPhotoIds = edit.removedPhotoIds,
    )

    override suspend fun receiptPhotoPaths(logId: String) = dao.receiptPhotoPaths(logId)

    override suspend fun saveReceipt(logId: String, photo: NewPhoto?, total: Money?, updatedAt: Long) =
        dao.saveReceipt(logId, photo?.toEntity(logId), total?.amountMinor, total?.currency, updatedAt)

    private fun NewPhoto.toEntity(logId: String) =
        PhotoEntity(id, logId, path, takenAt, if (kind == PhotoKind.RECEIPT) "receipt" else "meal")
}

/** 导入店铺文件。 */
interface PlaceImportRepository {
    /** 一个事务：店铺（含标签）、菜单原照片、菜品。 */
    suspend fun importPlace(place: Place, scans: List<NewMenuScan>, dishes: List<Dish>, createdAt: Long)
}

internal class RoomPlaceImportRepository(private val db: OpenBiteDatabase) : PlaceImportRepository {
    override suspend fun importPlace(place: Place, scans: List<NewMenuScan>, dishes: List<Dish>, createdAt: Long) {
        db.importDao().insert(
            place = place.toEntity(createdAt),
            tags = place.tags.distinct().map { com.openbite.core.database.entity.PlaceTagEntity(place.id, it) },
            scans = scans.map {
                MenuScanEntity(it.id, it.placeId, it.photoPath, it.model, it.sourceLang, it.targetLang, it.rawOutput, it.createdAt)
            },
            dishes = dishes.map { it.toEntity() },
        )
    }
}
