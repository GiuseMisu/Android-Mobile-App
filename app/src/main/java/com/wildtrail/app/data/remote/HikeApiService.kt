package com.wildtrail.app.data.remote

import com.wildtrail.app.data.remote.dto.PredictRequestDto
import com.wildtrail.app.data.remote.dto.PredictResponseDto
import com.wildtrail.app.data.remote.dto.SummarizeReviewsRequestDto
import com.wildtrail.app.data.remote.dto.SummarizeReviewsResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface HikeApiService {

    @POST("predict")
    suspend fun predict(@Body request: PredictRequestDto): PredictResponseDto

    @POST("summarize-reviews")
    suspend fun summarizeReviews(
        @Body request: SummarizeReviewsRequestDto,
    ): SummarizeReviewsResponseDto
}
