package com.wildtrail.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wildtrail.app.data.local.entity.EmergencyContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: EmergencyContactEntity)

    @Query("SELECT * FROM emergency_contacts WHERE userUid = :uid ORDER BY isPrimary DESC, name ASC")
    fun observeForUser(uid: String): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE userUid = :uid AND isPrimary = 1 LIMIT 1")
    suspend fun getPrimaryFor(uid: String): EmergencyContactEntity?

    @Query("SELECT * FROM emergency_contacts WHERE userUid = :uid AND notifyOnFall = 1")
    suspend fun getFallNotifyList(uid: String): List<EmergencyContactEntity>

    @Query("DELETE FROM emergency_contacts WHERE contactId = :id")
    suspend fun deleteById(id: String)
}
