package com.wildtrail.app.domain.model

data class UserFollow(
    val followerUid: String,
    val followeeUid: String,
    val notifyOnNewHike: Boolean,
    val createdAt: Long,
)

data class FollowedTrail(
    val userUid: String,
    val hikeId: String,
    val notifyOnNewReview: Boolean,
    val createdAt: Long,
)

data class HikeComment(
    val commentId: String,
    val authorUid: String,
    val hikeId: String,
    val text: String,
    val photoUrls: List<String>,
    val createdAt: Long,
)
