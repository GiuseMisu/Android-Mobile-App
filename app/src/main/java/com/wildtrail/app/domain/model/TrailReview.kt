package com.wildtrail.app.domain.model

data class TrailReview(
    val reviewId: String,
    val reviewerUid: String,
    val hikeId: String,
    val overallRating: Int,
    val fatigueLevel: Int,
    val pathClarity: Int,
    val difficultyLevel: Int,
    val mudRisk: Int,
    val animalEncounterRisk: Int,
    val waterAvailability: Boolean,
    val commentText: String? = null,
    val imageUrls: List<String> = emptyList(),
    val createdAt: Long,
)
