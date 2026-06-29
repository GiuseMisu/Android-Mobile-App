package com.wildtrail.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wildtrail.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(users: List<UserEntity>)

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM users WHERE firebaseUid = :uid LIMIT 1")
    suspend fun getById(uid: String): UserEntity?

    @Query("SELECT * FROM users WHERE firebaseUid = :uid LIMIT 1")
    fun observeById(uid: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isPublic = 1 ORDER BY xpPoints DESC LIMIT :limit")
    fun observeLeaderboard(limit: Int = 100): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE username LIKE '%' || :q || '%' AND isPublic = 1 LIMIT 50")
    suspend fun searchByUsername(q: String): List<UserEntity>

    @Query("DELETE FROM users WHERE firebaseUid = :uid")
    suspend fun deleteById(uid: String)

    @Query("DELETE FROM users")
    suspend fun clear()
}
