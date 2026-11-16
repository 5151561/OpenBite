package com.openbite.feature.meal

import com.openbite.core.data.MealEdit
import com.openbite.core.data.MealItemEdit
import com.openbite.core.data.NewPhoto
import com.openbite.core.model.DiningLog
import com.openbite.core.model.Money
import com.openbite.core.model.Photo
import com.openbite.core.model.PhotoKind

/** 补记忆里的一道菜。 */
data class EditItem(
    val id: String,
    val name: String,
    val nameTranslated: String?,
    val price: Money?,
    val qty: Int,
    val rating: Int?,
    val comment: String,
)

/**
 * 补记忆（设计稿 05）的编辑状态。纯数据，改动都返回新对象，保存时 [toEdit] 变成要写回数据库的内容。
 * 新加的照片文件已经存在 photos/ 下，但还没进数据库；放弃编辑时要删掉 [addedPhotos] 的文件。
 */
data class MealEditor(
    val logId: String,
    val rating: Int?,
    val note: String,
    val items: List<EditItem>,
    val removedItemIds: List<String> = emptyList(),
    val milestoneId: String?,
    val personIds: Set<String>,
    /** 已经存过的饭菜照片（不含小票）。 */
    val photos: List<Photo>,
    val removedPhotos: List<Photo> = emptyList(),
    val addedPhotos: List<NewPhoto> = emptyList(),
    val dirty: Boolean = false,
) {
    fun setRating(value: Int) = copy(rating = if (rating == value) null else value.coerceIn(1, 5), dirty = true)

    fun setNote(value: String) = copy(note = value, dirty = true)

    fun setItemRating(itemId: String, value: Int) = updateItem(itemId) { it.copy(rating = if (it.rating == value) null else value.coerceIn(1, 5)) }

    fun setItemComment(itemId: String, value: String) = updateItem(itemId) { it.copy(comment = value) }

    /** 份数减到 0 就是删掉这道菜（点错了、没上）。 */
    fun setQty(itemId: String, qty: Int): MealEditor =
        if (qty <= 0) removeItem(itemId) else updateItem(itemId) { it.copy(qty = qty.coerceAtMost(MAX_QTY)) }

    fun removeItem(itemId: String) = copy(
        items = items.filterNot { it.id == itemId },
        removedItemIds = if (items.any { it.id == itemId }) removedItemIds + itemId else removedItemIds,
        dirty = true,
    )

    /** 再点一次已选中的纪念日就是取消。 */
    fun toggleMilestone(id: String) = copy(milestoneId = if (milestoneId == id) null else id, dirty = true)

    fun togglePerson(id: String) = copy(personIds = if (id in personIds) personIds - id else personIds + id, dirty = true)

    fun addPhoto(photo: NewPhoto) = copy(addedPhotos = addedPhotos + photo, dirty = true)

    fun removePhoto(id: String): MealEditor {
        addedPhotos.firstOrNull { it.id == id }?.let { added -> return copy(addedPhotos = addedPhotos - added, dirty = true) }
        val existing = photos.firstOrNull { it.id == id } ?: return this
        return copy(photos = photos - existing, removedPhotos = removedPhotos + existing, dirty = true)
    }

    /** 新加的照片里，被删掉的那张文件要马上删，返回它的路径。 */
    fun addedPhotoPath(id: String): String? = addedPhotos.firstOrNull { it.id == id }?.path

    /** 按菜单价格算的合计（小票没记时显示它）。 */
    val orderedTotal: Money?
        get() {
            if (items.isEmpty()) return null
            val currency = items.first().price?.currency ?: return null
            if (items.any { it.price?.currency != currency }) return null
            return Money(items.sumOf { it.price!!.amountMinor * it.qty }, currency)
        }

    fun toEdit(done: Boolean, updatedAt: Long) = MealEdit(
        logId = logId,
        rating = rating,
        note = note.trim().takeIf { it.isNotEmpty() },
        milestoneId = milestoneId,
        done = done,
        items = items.map { MealItemEdit(it.id, it.qty, it.rating, it.comment.trim().takeIf { c -> c.isNotEmpty() }) },
        removedItemIds = removedItemIds,
        personIds = personIds.toList(),
        addedPhotos = addedPhotos,
        removedPhotoIds = removedPhotos.map { it.id },
        updatedAt = updatedAt,
    )

    private fun updateItem(itemId: String, transform: (EditItem) -> EditItem) =
        copy(items = items.map { if (it.id == itemId) transform(it) else it }, dirty = true)

    companion object {
        const val MAX_QTY = 99

        fun from(log: DiningLog) = MealEditor(
            logId = log.id,
            rating = log.rating,
            note = log.note.orEmpty(),
            items = log.items.map { EditItem(it.id, it.name, it.nameTranslated, it.price, it.qty, it.rating, it.comment.orEmpty()) },
            milestoneId = log.milestoneId,
            personIds = log.companions.map { it.id }.toSet(),
            photos = log.photos.filter { it.kind == PhotoKind.MEAL },
        )
    }
}
