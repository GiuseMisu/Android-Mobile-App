package com.wildtrail.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.UserRepository
import com.wildtrail.app.domain.model.HikeFilter
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.SurfaceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SortOption(val label: String) {
    RECENT("Recent"),
    MOST_LIKED("Liked"),
    TOP_RATED("Top rated"),
    LONGEST("Longest"),
}

data class ExploreUiState(
    val query: String = "",
    val sort: SortOption = SortOption.RECENT,
    val isFilterMenuOpen: Boolean = false,
    val draftFilter: HikeFilter = HikeFilter(),
    val appliedFilter: HikeFilter = HikeFilter(),
    val results: List<HikeLog> = emptyList(),
    val featured: List<HikeLog> = emptyList(),
    val showingResults: Boolean = false,
    val likedHikeIds: Set<String> = emptySet(),
    val currentUserUid: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel(
    private val hikeLogRepository: HikeLogRepository,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortOption.RECENT)
    private val isFilterMenuOpen = MutableStateFlow(false)
    private val draftFilter = MutableStateFlow(HikeFilter())
    private val appliedFilter = MutableStateFlow(HikeFilter())

    // re-queries when the applied filter changes; likes stay live via the underlying Flow
    private val results: kotlinx.coroutines.flow.Flow<List<HikeLog>> =
        combine(query, appliedFilter) { q, filter -> q to filter }
            .flatMapLatest { (q, filter) ->
                if (q.isBlank() && !filter.isActive()) {
                    flowOf(emptyList<HikeLog>())
                } else {
                    hikeLogRepository.filter(q, filter)
                }
            }

    private val featured = userRepository.withLiveCreatorPictures(
        hikeLogRepository.observePublicFeed(20),
    )
    private val enrichedResults = userRepository.withLiveCreatorPictures(results)

    private val currentUidFlow = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.user?.firebaseUid }

    private val likedHikeIds = currentUidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptySet()) else hikeLogRepository.observeMyLikedHikeIds(uid)
    }

    private data class Controls(
        val query: String,
        val sort: SortOption,
        val isFilterMenuOpen: Boolean,
        val draftFilter: HikeFilter,
        val appliedFilter: HikeFilter,
    )

    private val controls = combine(
        query,
        sort,
        isFilterMenuOpen,
        draftFilter,
        appliedFilter,
    ) { q, s, open, draft, applied -> Controls(q, s, open, draft, applied) }

    val uiState: StateFlow<ExploreUiState> = combine(
        controls,
        enrichedResults,
        featured,
        likedHikeIds,
        currentUidFlow,
    ) { c, r, featuredHikes, liked, uid ->
        ExploreUiState(
            query = c.query,
            sort = c.sort,
            isFilterMenuOpen = c.isFilterMenuOpen,
            draftFilter = c.draftFilter,
            appliedFilter = c.appliedFilter,
            results = sortHikes(r, c.sort),
            featured = sortHikes(featuredHikes, c.sort),
            showingResults = c.query.isNotBlank() || c.appliedFilter.isActive(),
            likedHikeIds = liked,
            currentUserUid = uid,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ExploreUiState(),
    )

    fun onQueryChanged(q: String) {
        query.value = q
    }

    fun onSortChanged(option: SortOption) {
        sort.value = option
    }

    fun onToggleFilterMenu() {
        isFilterMenuOpen.value = !isFilterMenuOpen.value
    }

    fun onDistanceChange(range: ClosedFloatingPointRange<Float>) {
        draftFilter.value = draftFilter.value.copy(
            minKm = range.start,
            maxKm = range.endInclusive,
        )
    }

    fun onElevationChange(range: ClosedFloatingPointRange<Float>) {
        draftFilter.value = draftFilter.value.copy(
            minElevation = range.start.roundToInt(),
            maxElevation = range.endInclusive.roundToInt(),
        )
    }

    fun onDifficultyToggle(level: Int) {
        val current = draftFilter.value.difficulties
        draftFilter.value = draftFilter.value.copy(
            difficulties = if (level in current) current - level else current + level,
        )
    }

    fun onSurfaceTypeToggle(type: SurfaceType) {
        val current = draftFilter.value.surfaceTypes
        draftFilter.value = draftFilter.value.copy(
            surfaceTypes = if (type in current) current - type else current + type,
        )
    }

    fun onResetFilters() {
        draftFilter.value = HikeFilter()
    }

    fun onApplyFilters() {
        appliedFilter.value = draftFilter.value
        isFilterMenuOpen.value = false
    }

    private fun sortHikes(list: List<HikeLog>, sort: SortOption): List<HikeLog> = when (sort) {
        SortOption.RECENT -> list.sortedByDescending { it.endedAt }
        SortOption.MOST_LIKED -> list.sortedByDescending { it.likesCount }
        SortOption.TOP_RATED -> list.sortedWith(
            compareByDescending<HikeLog> { it.averageRating }.thenByDescending { it.reviewCount },
        )
        SortOption.LONGEST -> list.sortedByDescending { it.lengthKm }
    }

    suspend fun refresh() {
        runCatching { hikeLogRepository.refresh() }
    }

    fun toggleLike(hike: HikeLog) {
        val uid = uiState.value.currentUserUid ?: return
        viewModelScope.launch {
            runCatching {
                hikeLogRepository.setLiked(uid, hike.hikeId, hike.hikeId !in uiState.value.likedHikeIds)
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                ExploreViewModel(
                    hikeLogRepository = app.container.hikeLogRepository,
                    authRepository = app.container.authRepository,
                    userRepository = app.container.userRepository,
                )
            }
        }
    }
}
