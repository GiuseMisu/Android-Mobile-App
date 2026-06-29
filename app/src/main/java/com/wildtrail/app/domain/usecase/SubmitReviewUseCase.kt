package com.wildtrail.app.domain.usecase

import android.net.Uri
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.SocialRepository
import com.wildtrail.app.domain.model.TrailReview

class SubmitReviewUseCase(
    private val socialRepository: SocialRepository,
    private val hikeLogRepository: HikeLogRepository,
) {
    suspend operator fun invoke(
        review: TrailReview,
        localImageUris: List<Uri> = emptyList(),
    ): Result<Unit> = runCatching {
        socialRepository.submitReview(review, localImageUris)
        hikeLogRepository.refreshAggregateRating(review.hikeId)
    }
}
