package com.wildtrail.app.ui.home

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
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.User
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentUser: User? = null,
    val recentHikes: List<HikeLog> = emptyList(),
    val publicFeed: List<HikeLog> = emptyList(),
    val likedHikeIds: Set<String> = emptySet(),
    val isOffline: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val hikeLogRepository: HikeLogRepository,
) : ViewModel() {

    private val publicFeed: Flow<List<HikeLog>> =
        userRepository.withLiveCreatorPictures(hikeLogRepository.observePublicFeed(20))

    private val currentUidFlow = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.user?.firebaseUid }

    private val currentUser: Flow<User?> = currentUidFlow
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null) else userRepository.observeUser(uid)
        }

    private val myHikes: Flow<List<HikeLog>> = currentUidFlow
        .flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                userRepository.withLiveCreatorPictures(hikeLogRepository.observeMyHikes(uid))
            }
        }

    private val likedHikeIds: Flow<Set<String>> = currentUidFlow
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptySet()) else hikeLogRepository.observeMyLikedHikeIds(uid)
        }

    val uiState: StateFlow<HomeUiState> = combine(
        currentUser,
        publicFeed,
        myHikes,
        likedHikeIds,
    ) { user, publicHikes, mine, liked ->
        HomeUiState(
            currentUser = user,
            recentHikes = mine,
            publicFeed = publicHikes,
            likedHikeIds = liked,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HomeUiState(),
    )

    fun signOut() = authRepository.signOut()

    suspend fun refresh() {
        runCatching { hikeLogRepository.refresh() }
    }

    fun toggleLike(hike: HikeLog) {
        val uid = uiState.value.currentUser?.firebaseUid ?: return
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
                HomeViewModel(
                    authRepository = app.container.authRepository,
                    userRepository = app.container.userRepository,
                    hikeLogRepository = app.container.hikeLogRepository,
                )
            }
        }
    }
}
