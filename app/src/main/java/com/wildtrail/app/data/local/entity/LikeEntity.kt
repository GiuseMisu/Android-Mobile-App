package com.wildtrail.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import com.wildtrail.app.domain.model.Like

@Entity(
    tableName = "likes",
    primaryKeys = ["userUid", "hikeId"],
    indices = [Index("hikeId")],
)
data class LikeEntity(
    val userUid: String,
    val hikeId: String,
    val createdAt: Long,
)

fun LikeEntity.toDomain(): Like = Like(userUid, hikeId, createdAt)
fun Like.toEntity(): LikeEntity = LikeEntity(userUid, hikeId, createdAt)
