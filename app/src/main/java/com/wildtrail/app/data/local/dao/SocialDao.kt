package com.wildtrail.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wildtrail.app.data.local.entity.FollowedTrailEntity
import com.wildtrail.app.data.local.entity.HikeCommentEntity
import com.wildtrail.app.data.local.entity.UserFollowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserFollowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(follow: UserFollowEntity)

    @Query("DELETE FROM user_follows WHERE followerUid = :follower AND followeeUid = :followee")
    suspend fun delete(follower: String, followee: String)

    @Query("SELECT * FROM user_follows WHERE followerUid = :uid")
    fun observeFollowing(uid: String): Flow<List<UserFollowEntity>>

    @Query("SELECT * FROM user_follows WHERE followeeUid = :uid")
    fun observeFollowers(uid: String): Flow<List<UserFollowEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM user_follows
             WHERE followerUid = :follower AND followeeUid = :followee
        )
        """,
    )
    fun observeIsFollowing(follower: String, followee: String): Flow<Boolean>
}

@Dao
interface FollowedTrailDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trail: FollowedTrailEntity)

    @Query("DELETE FROM followed_trails WHERE userUid = :uid AND hikeId = :hikeId")
    suspend fun delete(uid: String, hikeId: String)

    @Query("SELECT * FROM followed_trails WHERE userUid = :uid")
    fun observeForUser(uid: String): Flow<List<FollowedTrailEntity>>
}

@Dao
interface HikeCommentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(comment: HikeCommentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(comments: List<HikeCommentEntity>)

    @Query("SELECT * FROM hike_comments WHERE hikeId = :hikeId ORDER BY createdAt ASC")
    fun observeForHike(hikeId: String): Flow<List<HikeCommentEntity>>

    @Query("DELETE FROM hike_comments WHERE commentId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM hike_comments WHERE hikeId = :hikeId")
    suspend fun deleteForHike(hikeId: String)
}
