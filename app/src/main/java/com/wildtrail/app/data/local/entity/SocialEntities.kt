package com.wildtrail.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wildtrail.app.domain.model.FollowedTrail
import com.wildtrail.app.domain.model.HikeComment
import com.wildtrail.app.domain.model.UserFollow

@Entity(
    tableName = "user_follows",
    primaryKeys = ["followerUid", "followeeUid"],
    indices = [Index("followeeUid")],
)
data class UserFollowEntity(
    val followerUid: String,
    val followeeUid: String,
    val notifyOnNewHike: Boolean,
    val createdAt: Long,
)

fun UserFollowEntity.toDomain(): UserFollow = UserFollow(
    followerUid = followerUid,
    followeeUid = followeeUid,
    notifyOnNewHike = notifyOnNewHike,
    createdAt = createdAt,
)

fun UserFollow.toEntity(): UserFollowEntity = UserFollowEntity(
    followerUid = followerUid,
    followeeUid = followeeUid,
    notifyOnNewHike = notifyOnNewHike,
    createdAt = createdAt,
)

@Entity(
    tableName = "followed_trails",
    primaryKeys = ["userUid", "hikeId"],
    indices = [Index("hikeId")],
)
data class FollowedTrailEntity(
    val userUid: String,
    val hikeId: String,
    val notifyOnNewReview: Boolean,
    val createdAt: Long,
)

fun FollowedTrailEntity.toDomain(): FollowedTrail = FollowedTrail(
    userUid = userUid,
    hikeId = hikeId,
    notifyOnNewReview = notifyOnNewReview,
    createdAt = createdAt,
)

fun FollowedTrail.toEntity(): FollowedTrailEntity = FollowedTrailEntity(
    userUid = userUid,
    hikeId = hikeId,
    notifyOnNewReview = notifyOnNewReview,
    createdAt = createdAt,
)

@Entity(
    tableName = "hike_comments",
    indices = [Index("hikeId"), Index("authorUid")],
)
data class HikeCommentEntity(
    @PrimaryKey val commentId: String,
    val authorUid: String,
    val hikeId: String,
    val text: String,
    val photoUrls: List<String>,
    val createdAt: Long,
)

fun HikeCommentEntity.toDomain(): HikeComment = HikeComment(
    commentId = commentId,
    authorUid = authorUid,
    hikeId = hikeId,
    text = text,
    photoUrls = photoUrls,
    createdAt = createdAt,
)

fun HikeComment.toEntity(): HikeCommentEntity = HikeCommentEntity(
    commentId = commentId,
    authorUid = authorUid,
    hikeId = hikeId,
    text = text,
    photoUrls = photoUrls,
    createdAt = createdAt,
)
