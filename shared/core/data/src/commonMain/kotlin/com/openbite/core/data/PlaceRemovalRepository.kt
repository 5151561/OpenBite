package com.openbite.core.data

import com.openbite.core.database.OpenBiteDatabase
import com.openbite.core.model.PlaceDeletionSummary

/** 删掉一家店后，事务外还要处理的：照片文件和被删的用餐记录 id。 */
data class RemovedPlace(val photoPaths: List<String>, val logIds: List<String>)

/** 删除店铺。只给界面用，系统助手（AppFunctions）不暴露。 */
interface PlaceRemovalRepository {
    /** 店不在返回 null。 */
    suspend fun summary(placeId: String): PlaceDeletionSummary?

    /** 在一个事务里删掉店铺和它的全部数据（纪念日、人保留）。店不在返回 null。 */
    suspend fun delete(placeId: String): RemovedPlace?
}

internal class RoomPlaceRemovalRepository(private val db: OpenBiteDatabase) : PlaceRemovalRepository {
    private val dao get() = db.maintenanceDao()

    override suspend fun summary(placeId: String): PlaceDeletionSummary? {
        val name = dao.placeName(placeId) ?: return null
        val photos = dao.photoPathsOf(placeId).size
        return PlaceDeletionSummary(name, dao.dishCountOf(placeId), dao.logCountOf(placeId), photos)
    }

    override suspend fun delete(placeId: String): RemovedPlace? =
        dao.deletePlace(placeId)?.let { RemovedPlace(it.photoPaths, it.logIds) }
}
