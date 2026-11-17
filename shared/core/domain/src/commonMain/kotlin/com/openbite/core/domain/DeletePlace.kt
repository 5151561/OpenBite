package com.openbite.core.domain

import com.openbite.core.data.PlaceRemovalRepository
import com.openbite.core.data.platform.ReminderScheduler
import com.openbite.core.model.PlaceDeletionSummary

/** 确认框要显示的：店名、几道菜、几条用餐记录、几张照片。店不在返回 null。 */
class PlaceDeletionSummaryUseCase(private val repository: PlaceRemovalRepository) {
    suspend operator fun invoke(placeId: String): PlaceDeletionSummary? = repository.summary(placeId)
}

/**
 * 删除店铺（界面要先让用户确认；助手不能调）。数据库部分在一个事务里；
 * 提交后取消这家店记录的饭后提醒，照片文件在应用级作用域里删（页面马上出栈也会删完）。
 * 店不在返回 false。
 */
class DeletePlaceUseCase(
    private val repository: PlaceRemovalRepository,
    private val discardPhotos: DiscardPhotosUseCase,
    private val reminders: ReminderScheduler,
) {
    suspend operator fun invoke(placeId: String): Boolean {
        val removed = repository.delete(placeId) ?: return false
        removed.logIds.forEach { runCatching { reminders.cancelMealFollowUp(it) } }
        discardPhotos.inBackground(removed.photoPaths)
        return true
    }
}
