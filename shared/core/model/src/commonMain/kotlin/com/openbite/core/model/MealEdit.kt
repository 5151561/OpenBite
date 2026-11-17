package com.openbite.core.model

/** 补记忆要写回的内容。只改这些字段，名字和价格快照不动。 */
data class MealEdit(
    val logId: String,
    val rating: Int?,
    val note: String?,
    val milestoneId: String?,
    val done: Boolean,
    /** 留下来的菜：份数、评分、短评。 */
    val items: List<MealItemEdit>,
    val removedItemIds: List<String>,
    val personIds: List<String>,
    val addedPhotos: List<NewPhoto>,
    val removedPhotoIds: List<String>,
    val updatedAt: Long,
)

data class MealItemEdit(val id: String, val qty: Int, val rating: Int?, val comment: String?)

data class NewPhoto(val id: String, val path: String, val kind: PhotoKind, val takenAt: Long?)
