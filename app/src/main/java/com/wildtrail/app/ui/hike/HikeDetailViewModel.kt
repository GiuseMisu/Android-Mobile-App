package com.wildtrail.app.ui.hike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.data.repository.PredictRepository
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.SocialRepository
import com.wildtrail.app.data.repository.UserRepository
import com.wildtrail.app.domain.model.HikeComment
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.TrailReview
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.domain.usecase.GetReviewSummaryUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface PredictionState {
    data object Idle : PredictionState
    data object Loading : PredictionState
    data class Success(val minutes: Double) : PredictionState
    data class Error(val message: String) : PredictionState
}

sealed interface ReviewSummaryState {
    data object Idle : ReviewSummaryState
    data object Loading : ReviewSummaryState
    data class Success(val summary: String) : ReviewSummaryState
    data class Error(val message: String) : ReviewSummaryState
}

data class HikeDetailUiState(
    val loading: Boolean = true,
    val hike: HikeLog? = null,
    val creator: User? = null,
    val reviews: List<TrailReview> = emptyList(),
    val comments: List<HikeComment> = emptyList(),
    val authors: Map<String, User> = emptyMap(),
    val likeCount: Int = 0,
    val isLikedByMe: Boolean = false,
    val currentUserUid: String? = null,
    val isMyHike: Boolean = false,
    val myReviewExists: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HikeDetailViewModel(
    private val hikeId: String,
    private val hikeLogRepository: HikeLogRepository,
    private val socialRepository: SocialRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val predictRepository: PredictRepository,
    private val getReviewSummaryUseCase: GetReviewSummaryUseCase,
) : ViewModel() {

    // separate from uiState so prediction/summary updates don't retrigger its big combine
    private val _predictionState = MutableStateFlow<PredictionState>(PredictionState.Idle)
    val predictionState: StateFlow<PredictionState> = _predictionState.asStateFlow()

    fun requestPrediction() {
        val hike = uiState.value.hike ?: return
        val uid  = uiState.value.currentUserUid ?: return

        viewModelScope.launch {
            _predictionState.value = PredictionState.Loading

            val user = userRepository.observeUser(uid).first()
            if (user == null) {
                _predictionState.value =
                    PredictionState.Error("Could not load your profile. Please try again.")
                return@launch
            }

            predictRepository.predict(user = user, hike = hike).fold(
                onSuccess = { minutes ->
                    _predictionState.value = PredictionState.Success(minutes)
                },
                onFailure = {
                    _predictionState.value =
                        PredictionState.Error("Prediction failed. Check your connection and try again.")
                },
            )
        }
    }

    private val _summaryState = MutableStateFlow<ReviewSummaryState>(ReviewSummaryState.Idle)
    val summaryState: StateFlow<ReviewSummaryState> = _summaryState.asStateFlow()

    fun summarizeReviews() {
        if (_summaryState.value is ReviewSummaryState.Loading) return
        val reviewTexts = uiState.value.reviews
            .mapNotNull { it.commentText?.takeIf { t -> t.isNotBlank() } }

        _summaryState.value = ReviewSummaryState.Loading
        viewModelScope.launch {
            getReviewSummaryUseCase(reviewTexts).fold(
                onSuccess = { summary ->
                    _summaryState.value = ReviewSummaryState.Success(summary)
                },
                onFailure = { t ->
                    val message = if (t is GetReviewSummaryUseCase.NoReviewsException) {
                        "No written reviews to summarize yet."
                    } else {
                        "Couldn't generate a summary. Check your connection and try again."
                    }
                    _summaryState.value = ReviewSummaryState.Error(message)
                },
            )
        }
    }

    private val authorsCache = MutableStateFlow<Map<String, User>>(emptyMap())

    private val subscribedUids = mutableSetOf<String>()

    private fun ensureUserSubscribed(uid: String) {
        if (!subscribedUids.add(uid)) return
        userRepository.observeUser(uid)
            .onEach { user ->
                if (user != null) {
                    authorsCache.value = authorsCache.value + (uid to user)
                }
            }
            .launchIn(viewModelScope)
    }

    private val currentUidFlow = authRepository.authState.map {
        (it as? AuthState.SignedIn)?.user?.firebaseUid
    }

    private val hikeFlow = hikeLogRepository.observeHike(hikeId)

    private val reviewsFlow = socialRepository.observeReviewsForHike(hikeId)
        .onEach { rs -> rs.forEach { ensureUserSubscribed(it.reviewerUid) } }
    private val commentsFlow = socialRepository.observeComments(hikeId)
        .onEach { cs -> cs.forEach { ensureUserSubscribed(it.authorUid) } }

    private val likesFlow = currentUidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(false to 0)
        } else {
            combine(
                hikeLogRepository.observeIsLiked(uid, hikeId),
                hikeLogRepository.observeLikeCount(hikeId),
            ) { liked, count -> liked to count }
        }
    }

    val uiState: StateFlow<HikeDetailUiState> = combine(
        hikeFlow,
        reviewsFlow,
        commentsFlow,
        likesFlow,
        currentUidFlow,
    ) { hike, reviews, comments, likesPair, uid ->
        val (liked, count) = likesPair
        if (hike != null) ensureUserSubscribed(hike.creatorFirebaseUid)
        HikeDetailUiState(
            loading = false,
            hike = hike,
            creator = null,
            reviews = reviews,
            comments = comments,
            authors = emptyMap(),
            likeCount = count,
            isLikedByMe = liked,
            currentUserUid = uid,
            isMyHike = hike != null && uid != null && hike.creatorFirebaseUid == uid,
            myReviewExists = uid != null && reviews.any { it.reviewerUid == uid },
        )
    }
        .combine(authorsCache) { state, authors ->
            state.copy(
                authors = authors,
                creator = state.hike?.creatorFirebaseUid?.let(authors::get),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = HikeDetailUiState(loading = true),
        )

    fun postComment(text: String) {
        val auth = authRepository.authState.value as? AuthState.SignedIn ?: return
        val comment = HikeComment(
            commentId = UUID.randomUUID().toString(),
            authorUid = auth.user.firebaseUid,
            hikeId = hikeId,
            text = text,
            photoUrls = emptyList(),
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { runCatching { socialRepository.postComment(comment) } }
    }

    fun toggleLike() {
        val state = uiState.value
        val uid = state.currentUserUid ?: return
        viewModelScope.launch {
            runCatching { hikeLogRepository.setLiked(uid, hikeId, !state.isLikedByMe) }
        }
    }

    /** Deletes the hike if it belongs to the current user, then invokes [onDeleted] (e.g. navigate back). */
    fun deleteHike(onDeleted: () -> Unit) {
        val state = uiState.value
        val hike = state.hike ?: return
        if (!state.isMyHike) return
        viewModelScope.launch {
            runCatching { hikeLogRepository.deleteHike(hike) }
            onDeleted()
        }
    }

    fun deleteReview(reviewId: String) {
        val state = uiState.value
        val uid = state.currentUserUid ?: return
        val review = state.reviews.firstOrNull { it.reviewId == reviewId } ?: return
        if (review.reviewerUid != uid) return
        viewModelScope.launch {
            runCatching {
                socialRepository.deleteReview(review)
                hikeLogRepository.refreshAggregateRating(review.hikeId)
            }
        }
    }

    fun deleteComment(commentId: String) {
        val state = uiState.value
        val uid = state.currentUserUid ?: return
        val comment = state.comments.firstOrNull { it.commentId == commentId } ?: return
        if (comment.authorUid != uid) return
        viewModelScope.launch {
            runCatching { socialRepository.deleteComment(commentId) }
        }
    }

    suspend fun refresh() {
        runCatching { hikeLogRepository.refresh() }
    }

    companion object {
        const val ARG_HIKE_ID = "hikeId"

        fun factory(hikeId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                HikeDetailViewModel(
                    hikeId = hikeId,
                    hikeLogRepository = app.container.hikeLogRepository,
                    socialRepository = app.container.socialRepository,
                    userRepository = app.container.userRepository,
                    authRepository = app.container.authRepository,
                    predictRepository = app.container.predictRepository,
                    getReviewSummaryUseCase = GetReviewSummaryUseCase(
                        app.container.reviewSummaryRepository,
                    ),
                )
            }
        }
    }
}
