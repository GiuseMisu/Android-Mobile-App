package com.wildtrail.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SummarizeReviewsRequestDto(
    val reviews: List<String>,
)

data class SummarizeReviewsResponseDto(
    val summary: String,
    @SerializedName("review_count") val reviewCount: Int = 0,
)
