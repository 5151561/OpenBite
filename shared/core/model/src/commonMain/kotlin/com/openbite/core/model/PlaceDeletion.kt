package com.openbite.core.model

/** 删除一家店前给用户看的：会一起删掉多少东西。[photoCount] 含菜单原照片、饭菜照片和小票照片。 */
data class PlaceDeletionSummary(
    val placeName: String,
    val dishCount: Int,
    val logCount: Int,
    val photoCount: Int,
)
