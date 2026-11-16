package com.openbite.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

data class PlaceWithTags(
    @Embedded val place: PlaceEntity,
    @Relation(parentColumn = "id", entityColumn = "placeId")
    val tags: List<PlaceTagEntity>,
)

data class DiningLogWithDetails(
    @Embedded val log: DiningLogEntity,
    @Relation(entity = PlaceEntity::class, parentColumn = "placeId", entityColumn = "id")
    val place: PlaceWithTags,
    @Relation(parentColumn = "id", entityColumn = "logId")
    val items: List<DiningLogItemEntity>,
)
