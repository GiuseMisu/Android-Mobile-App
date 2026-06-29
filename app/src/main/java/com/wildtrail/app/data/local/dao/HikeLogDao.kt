package com.wildtrail.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wildtrail.app.data.local.entity.HikeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HikeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hike: HikeLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(hikes: List<HikeLogEntity>)

    @Query("SELECT * FROM hike_logs WHERE hikeId = :id LIMIT 1")
    suspend fun getById(id: String): HikeLogEntity?

    @Query("SELECT * FROM hike_logs WHERE hikeId = :id LIMIT 1")
    fun observeById(id: String): Flow<HikeLogEntity?>

    @Query("SELECT * FROM hike_logs WHERE creatorFirebaseUid = :uid ORDER BY endedAt DESC")
    fun observeByCreator(uid: String): Flow<List<HikeLogEntity>>

    @Query("SELECT * FROM hike_logs WHERE creatorFirebaseUid = :uid")
    suspend fun getByCreator(uid: String): List<HikeLogEntity>

    @Query(
        """
        SELECT h.* FROM hike_logs h
         INNER JOIN likes l ON l.hikeId = h.hikeId
         WHERE l.userUid = :uid
         ORDER BY l.createdAt DESC
        """,
    )
    fun observeLikedHikes(uid: String): Flow<List<HikeLogEntity>>

    @Query("SELECT * FROM hike_logs WHERE isPrivate = 0 ORDER BY endedAt DESC LIMIT :limit")
    fun observePublicFeed(limit: Int = 50): Flow<List<HikeLogEntity>>

    @Query(
        """
        SELECT * FROM hike_logs
         WHERE isPrivate = 0
           AND (title LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%')
         ORDER BY likesCount DESC
         LIMIT 100
        """,
    )
    suspend fun search(q: String): List<HikeLogEntity>

    @Query(
        """
        SELECT * FROM hike_logs
         WHERE isPrivate = 0
           AND (:q = '' OR title LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%')
           AND lengthKm BETWEEN :minKm AND :maxKm
           AND elevationGainMeters BETWEEN :minElevation AND :maxElevation
           AND (:anyDifficulty OR difficultyLevel IN (:difficulties))
           AND (:anySurface OR surfaceType IN (:surfaces))
         ORDER BY endedAt DESC
         LIMIT 200
        """,
    )
    fun filter(
        q: String,
        minKm: Float,
        maxKm: Float,
        minElevation: Int,
        maxElevation: Int,
        anyDifficulty: Boolean,
        difficulties: List<Int>,
        anySurface: Boolean,
        surfaces: List<String>,
    ): Flow<List<HikeLogEntity>>

    @Query("DELETE FROM hike_logs WHERE hikeId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM hike_logs")
    suspend fun clear()
}
