package com.openbite.core.domain

import com.openbite.core.data.MilestoneRepository
import com.openbite.core.data.PlaceRepository
import com.openbite.core.model.Milestone
import com.openbite.core.model.Place
import com.openbite.core.model.ScannedReceipt
import com.openbite.core.network.SampleVisionModel
import com.openbite.core.network.parseReceipt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

// 薄用例：ViewModel 只认用例，不直接碰 Repository（feature → domain → data）。

/** 全部纪念日（含已经过去的一次性纪念日）。 */
class ObserveAllMilestonesUseCase(private val milestones: MilestoneRepository) {
    operator fun invoke(): Flow<List<Milestone>> = milestones.observeMilestones()
}

/** 一家店的基本信息；删掉了是 null。 */
class ObservePlaceUseCase(private val places: PlaceRepository) {
    operator fun invoke(placeId: String): Flow<Place?> = places.observePlace(placeId)
}

/** 分享店铺页要显示的：店、菜单里几道菜、几张菜单照片。 */
data class SharePlaceSource(val place: Place?, val dishCount: Int, val photoCount: Int)

class ObserveSharePlaceUseCase(private val places: PlaceRepository) {
    operator fun invoke(placeId: String): Flow<SharePlaceSource> = combine(
        places.observePlace(placeId),
        places.observeMenu(placeId),
        places.observeScans(placeId),
    ) { place, menu, scans -> SharePlaceSource(place, menu.size, scans.size) }
}

/** 示例小票（界面预览用，和没 Key 时识别出来的一样）。 */
fun sampleReceipt(): ScannedReceipt? = parseReceipt(SampleVisionModel.SAMPLE_RECEIPT)
