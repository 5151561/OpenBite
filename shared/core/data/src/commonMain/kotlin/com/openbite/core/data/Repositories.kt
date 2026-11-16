package com.openbite.core.data

import com.openbite.core.data.seed.SeedImporter
import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.model.DiningLog
import com.openbite.core.model.Milestone
import com.openbite.core.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DiningLogRepository {
    fun observeLogs(): Flow<List<DiningLog>>
    suspend fun logsOfMilestone(milestoneId: String): List<DiningLog>
}

interface PlaceRepository {
    fun observePlaces(): Flow<List<Place>>
}

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
}

internal class RoomMilestoneRepository(private val db: OpenBiteDatabase) : MilestoneRepository {
    override fun observeMilestones() = db.milestoneDao().observeAll().map { rows -> rows.map { it.toModel() } }
}
