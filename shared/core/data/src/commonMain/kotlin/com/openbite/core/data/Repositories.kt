package com.openbite.core.data

import com.openbite.core.data.seed.SeedImporter
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.model.DiningLog
import com.openbite.core.model.Dish
import com.openbite.core.model.MenuScan
import com.openbite.core.model.Milestone
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface DiningLogRepository {
    fun observeLogs(): Flow<List<DiningLog>>
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
}

data class PlaceCounts(val dishes: Int, val photos: Int)

interface MilestoneRepository {
    fun observeMilestones(): Flow<List<Milestone>>
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
}

internal class RoomMilestoneRepository(private val db: OpenBiteDatabase) : MilestoneRepository {
    override fun observeMilestones() = db.milestoneDao().observeAll().map { rows -> rows.map { it.toModel() } }
}
