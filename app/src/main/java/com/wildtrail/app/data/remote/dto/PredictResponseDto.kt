package com.wildtrail.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PredictResponseDto(
    @SerializedName("duration_min") val durationMin: Double,
    val unit: String,
)
