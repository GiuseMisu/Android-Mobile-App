package com.wildtrail.app.domain.model

data class Like(
    val userUid: String,
    val hikeId: String,
    val createdAt: Long,
)
