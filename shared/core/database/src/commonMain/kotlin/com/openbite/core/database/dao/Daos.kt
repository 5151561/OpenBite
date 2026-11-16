package com.openbite.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.openbite.core.database.entity.DiningLogEntity
import com.openbite.core.database.entity.DiningLogItemEntity
import com.openbite.core.database.entity.DiningLogPersonEntity
import com.openbite.core.database.entity.DiningLogWithDetails
import com.openbite.core.database.entity.DishEntity
import com.openbite.core.database.entity.MilestoneEntity
import com.openbite.core.database.entity.MilestonePersonEntity
import com.openbite.core.database.entity.PersonEntity
import com.openbite.core.database.entity.PlaceEntity
import com.openbite.core.database.entity.PlaceTagEntity
import com.openbite.core.database.entity.PlaceWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Upsert
    suspend fun upsert(place: PlaceEntity)

    @Upsert
    suspend fun upsertTags(tags: List<PlaceTagEntity>)

    @Upsert
    suspend fun upsertDishes(dishes: List<DishEntity>)

    @Query("SELECT COUNT(*) FROM place")
    suspend fun count(): Int

    @Transaction
    @Query("SELECT * FROM place ORDER BY name")
    fun observeAll(): Flow<List<PlaceWithTags>>

    @Query("SELECT * FROM dish WHERE placeId = :placeId ORDER BY category, nameOriginal")
    suspend fun dishesOf(placeId: String): List<DishEntity>
}

@Dao
interface DiningLogDao {
    @Upsert
    suspend fun upsert(log: DiningLogEntity)

    @Upsert
    suspend fun upsertItems(items: List<DiningLogItemEntity>)

    @Upsert
    suspend fun upsertPersons(links: List<DiningLogPersonEntity>)

    @Transaction
    @Query("SELECT * FROM dining_log ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<DiningLogWithDetails>>

    @Transaction
    @Query("SELECT * FROM dining_log WHERE milestoneId = :milestoneId ORDER BY visitedAt DESC")
    suspend fun byMilestone(milestoneId: String): List<DiningLogWithDetails>
}

@Dao
interface MilestoneDao {
    @Upsert
    suspend fun upsert(milestone: MilestoneEntity)

    @Upsert
    suspend fun upsertPersons(persons: List<PersonEntity>)

    @Upsert
    suspend fun upsertLinks(links: List<MilestonePersonEntity>)

    @Query("SELECT * FROM milestone")
    fun observeAll(): Flow<List<MilestoneEntity>>
}

/** 首次启动导入种子数据，整批在一个事务里写入。 */
@Dao
abstract class SeedDao {
    @Insert
    protected abstract suspend fun insertPersons(items: List<PersonEntity>)

    @Insert
    protected abstract suspend fun insertMilestones(items: List<MilestoneEntity>)

    @Insert
    protected abstract suspend fun insertMilestonePersons(items: List<MilestonePersonEntity>)

    @Insert
    protected abstract suspend fun insertPlaces(items: List<PlaceEntity>)

    @Insert
    protected abstract suspend fun insertPlaceTags(items: List<PlaceTagEntity>)

    @Insert
    protected abstract suspend fun insertDishes(items: List<DishEntity>)

    @Insert
    protected abstract suspend fun insertLogs(items: List<DiningLogEntity>)

    @Insert
    protected abstract suspend fun insertLogItems(items: List<DiningLogItemEntity>)

    @Insert
    protected abstract suspend fun insertLogPersons(items: List<DiningLogPersonEntity>)

    @Transaction
    open suspend fun insertAll(
        persons: List<PersonEntity>,
        milestones: List<MilestoneEntity>,
        milestonePersons: List<MilestonePersonEntity>,
        places: List<PlaceEntity>,
        placeTags: List<PlaceTagEntity>,
        dishes: List<DishEntity>,
        logs: List<DiningLogEntity>,
        logItems: List<DiningLogItemEntity>,
        logPersons: List<DiningLogPersonEntity>,
    ) {
        insertPersons(persons)
        insertMilestones(milestones)
        insertMilestonePersons(milestonePersons)
        insertPlaces(places)
        insertPlaceTags(placeTags)
        insertDishes(dishes)
        insertLogs(logs)
        insertLogItems(logItems)
        insertLogPersons(logPersons)
    }
}
