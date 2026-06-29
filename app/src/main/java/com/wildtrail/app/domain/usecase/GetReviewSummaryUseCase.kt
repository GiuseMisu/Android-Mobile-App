package com.wildtrail.app.domain.usecase

import com.wildtrail.app.data.repository.ReviewSummaryRepository

class GetReviewSummaryUseCase(
    private val reviewSummaryRepository: ReviewSummaryRepository,
) {

    suspend operator fun invoke(reviewTexts: List<String>): Result<String> {
        val corpus = reviewTexts.map { it.trim() }.filter { it.isNotEmpty() }
        if (corpus.isEmpty()) {
            return Result.failure(NoReviewsException())
        }
        return reviewSummaryRepository.summarize(corpus)
    }

    class NoReviewsException : Exception("No written reviews to summarize yet")
}
