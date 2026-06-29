package com.wildtrail.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AchievementRepository
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.UserRepository
import com.wildtrail.app.domain.model.AchievementDefinition
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.domain.model.User
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

data class ProfileUiState(
    val user: User? = null,
    val hikes: List<HikeLog> = emptyList(),
    val earnedAchievements: List<AchievementDefinition> = emptyList(),
    val likedHikeIds: Set<String> = emptySet(),
    val currentUserUid: String? = null,
    val isMe: Boolean = true,
    val uploadingPicture: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val targetUid: String?,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val hikeLogRepository: HikeLogRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    private val currentUidFlow = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.user?.firebaseUid }

    init {
        viewModelScope.launch {
            runCatching { achievementRepository.syncDefinitions() }
            currentUidFlow
                .flatMapLatest { uid ->
                    if (uid == null) {
                        flowOf(null)
                    } else {
                        combine(
                            userRepository.observeUser(uid),
                            hikeLogRepository.observeMyHikes(uid),
                        ) { user, hikes -> user to hikes }
                    }
                }
                .collect { pair ->
                    val user = pair?.first ?: return@collect
                    runCatching {
                        achievementRepository.evaluateAndAward(user, pair.second)
                    }
                }
        }
    }

    private val uidFlow = if (targetUid != null) flowOf(targetUid) else currentUidFlow

    private val likedHikeIds = currentUidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptySet()) else hikeLogRepository.observeMyLikedHikeIds(uid)
    }

    private val uploadingPicture = MutableStateFlow(false)

    val uiState: StateFlow<ProfileUiState> = uidFlow
        .flatMapLatest { uid ->
            if (uid == null) flowOf(ProfileUiState())
            else combine(
                userRepository.observeUser(uid),
                userRepository.withLiveCreatorPictures(hikeLogRepository.observeMyHikes(uid)),
                achievementRepository.observeEarned(uid),
                currentUidFlow,
                likedHikeIds,
            ) { user, hikes, achievements, meUid, liked ->
                ProfileUiState(
                    user = user,
                    hikes = hikes,
                    earnedAchievements = achievements,
                    likedHikeIds = liked,
                    currentUserUid = meUid,
                    isMe = meUid != null && meUid == uid,
                )
            }
        }
        .combine(uploadingPicture) { state, uploading -> state.copy(uploadingPicture = uploading) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ProfileUiState(),
        )

    fun signOut() = authRepository.signOut()

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

    fun changeProfilePicture(localUri: android.net.Uri) {
        val uid = uiState.value.user?.firebaseUid ?: return
        uploadingPicture.value = true
        viewModelScope.launch {
            runCatching { userRepository.updateProfilePicture(uid, localUri) }
            uploadingPicture.value = false
        }
    }

    fun updateProfile(
        bio: String?,
        country: String?,
        emergencyContactNumber: String? = null,
        sex: Sex? = null,
        dateOfBirth: Long? = null,
    ) {
        val current = uiState.value.user ?: return
        viewModelScope.launch {
            runCatching {
                userRepository.updateUser(
                    current.copy(
                        bio = bio,
                        country = country,
                        emergencyContactNumber = emergencyContactNumber
                            ?: current.emergencyContactNumber,
                        sex = sex ?: current.sex,
                        dateOfBirth = dateOfBirth ?: current.dateOfBirth,
                    ),
                )
            }
        }
    }

    companion object {
        fun factory(targetUid: String? = null): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                ProfileViewModel(
                    targetUid = targetUid,
                    authRepository = app.container.authRepository,
                    userRepository = app.container.userRepository,
                    hikeLogRepository = app.container.hikeLogRepository,
                    achievementRepository = app.container.achievementRepository,
                )
            }
        }
    }
}
