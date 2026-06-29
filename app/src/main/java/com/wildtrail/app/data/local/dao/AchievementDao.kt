package com.wildtrail.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wildtrail.app.data.local.entity.AchievementDefinitionEntity
import com.wildtrail.app.data.local.entity.UserAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDefinitions(defs: List<AchievementDefinitionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEarned(earned: UserAchievementEntity)

    @Query("SELECT * FROM achievement_definitions ORDER BY xpReward ASC")
    fun observeAllDefinitions(): Flow<List<AchievementDefinitionEntity>>

    @Query(
        """
        SELECT d.* FROM achievement_definitions d
         INNER JOIN user_achievements ua ON ua.achievementId = d.achievementId
         WHERE ua.userUid = :uid
         ORDER BY ua.earnedAt DESC
        """,
    )
    fun observeEarnedFor(uid: String): Flow<List<AchievementDefinitionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM user_achievements WHERE userUid = :uid AND achievementId = :achievementId)")
    suspend fun hasEarned(uid: String, achievementId: String): Boolean
}
