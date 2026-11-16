package com.openbite.core.data

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
