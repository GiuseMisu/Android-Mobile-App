package com.wildtrail.app.ui.review

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.domain.model.TrailReview
import com.wildtrail.app.domain.usecase.SubmitReviewUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SubmitReviewUiState(
    val overallRating: Int = 3,
    val difficultyLevel: Int = 3,
    val mudRisk: Int = 3,
    val pathClarity: Int = 3,
    val fatigueLevel: Int = 3,
    val animalEncounterRisk: Int = 3,
    val waterAvailability: Boolean = false,
    val commentText: String = "",
    val imageUris: List<Uri> = emptyList(),
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
)

class SubmitReviewViewModel(
    private val hikeId: String,
    private val authRepository: AuthRepository,
    private val hikeLogRepository: HikeLogRepository,
    private val submitReviewUseCase: SubmitReviewUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubmitReviewUiState())
    val uiState: StateFlow<SubmitReviewUiState> = _uiState.asStateFlow()

    fun onOverallChange(value: Int) = _uiState.update { it.copy(overallRating = value) }
    fun onDifficultyChange(value: Int) = _uiState.update { it.copy(difficultyLevel = value) }
    fun onMudChange(value: Int) = _uiState.update { it.copy(mudRisk = value) }
    fun onClarityChange(value: Int) = _uiState.update { it.copy(pathClarity = value) }
    fun onFatigueChange(value: Int) = _uiState.update { it.copy(fatigueLevel = value) }
    fun onAnimalChange(value: Int) = _uiState.update { it.copy(animalEncounterRisk = value) }
    fun onWaterChange(value: Boolean) = _uiState.update { it.copy(waterAvailability = value) }
    fun onCommentChange(value: String) = _uiState.update { it.copy(commentText = value) }

    fun onAddImages(uris: List<Uri>) =
        _uiState.update { it.copy(imageUris = (it.imageUris + uris).distinct()) }

    fun onRemoveImage(uri: Uri) =
        _uiState.update { it.copy(imageUris = it.imageUris - uri) }

    fun onErrorShown() = _uiState.update { it.copy(error = null) }

    fun submit() {
        if (_uiState.value.isSubmitting) return
        val uid = (authRepository.authState.value as? AuthState.SignedIn)?.user?.firebaseUid
        if (uid == null) {
            _uiState.update { it.copy(error = "You must be signed in to leave a review.") }
            return
        }
        val snapshot = _uiState.value
        _uiState.update { it.copy(isSubmitting = true, error = null) }

        viewModelScope.launch {
            val hike = hikeLogRepository.observeHike(hikeId).first()
            if (hike != null && hike.creatorFirebaseUid == uid) {
                _uiState.update {
                    it.copy(isSubmitting = false, error = "You can't review your own hike.")
                }
                return@launch
            }

            val review = TrailReview(
                reviewId = UUID.randomUUID().toString(),
                reviewerUid = uid,
                hikeId = hikeId,
                overallRating = snapshot.overallRating,
                fatigueLevel = snapshot.fatigueLevel,
                pathClarity = snapshot.pathClarity,
                difficultyLevel = snapshot.difficultyLevel,
                mudRisk = snapshot.mudRisk,
                animalEncounterRisk = snapshot.animalEncounterRisk,
                waterAvailability = snapshot.waterAvailability,
                commentText = snapshot.commentText.trim().takeIf { it.isNotEmpty() },
                createdAt = System.currentTimeMillis(),
            )

            val result = submitReviewUseCase(review, snapshot.imageUris)
            _uiState.update { state ->
                if (result.isSuccess) {
                    state.copy(isSubmitting = false, submitted = true)
                } else {
                    state.copy(
                        isSubmitting = false,
                        error = "Couldn't submit your review. Please try again.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(hikeId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                SubmitReviewViewModel(
                    hikeId = hikeId,
                    authRepository = app.container.authRepository,
                    hikeLogRepository = app.container.hikeLogRepository,
                    submitReviewUseCase = SubmitReviewUseCase(
                        socialRepository = app.container.socialRepository,
                        hikeLogRepository = app.container.hikeLogRepository,
                    ),
                )
            }
        }
    }
}
