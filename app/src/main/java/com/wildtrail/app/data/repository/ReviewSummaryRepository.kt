package com.wildtrail.app.data.repository

import com.wildtrail.app.data.remote.HikeApiService
import com.wildtrail.app.data.remote.dto.SummarizeReviewsRequestDto

class ReviewSummaryRepository(
    private val hikeApiService: HikeApiService,
) {

    suspend fun summarize(reviews: List<String>): Result<String> = runCatching {
        require(reviews.isNotEmpty()) { "No reviews to summarize" }
        hikeApiService
            .summarizeReviews(SummarizeReviewsRequestDto(reviews))
            .summary
    }
}
