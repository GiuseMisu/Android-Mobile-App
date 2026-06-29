package com.wildtrail.app.ui.liked

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.UserRepository
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.ui.components.HikeCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LikedHikesUiState(
    val hikes: List<HikeLog> = emptyList(),
    val likedHikeIds: Set<String> = emptySet(),
    val currentUserUid: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LikedHikesViewModel(
    private val authRepository: AuthRepository,
    private val hikeLogRepository: HikeLogRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val currentUidFlow = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.user?.firebaseUid }

    private val likedHikes = currentUidFlow.flatMapLatest { uid ->
        if (uid == null) {
            flowOf(emptyList())
        } else {
            userRepository.withLiveCreatorPictures(hikeLogRepository.observeLikedHikes(uid))
        }
    }

    private val likedHikeIds = currentUidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptySet()) else hikeLogRepository.observeMyLikedHikeIds(uid)
    }

    val uiState: StateFlow<LikedHikesUiState> = combine(
        likedHikes,
        likedHikeIds,
        currentUidFlow,
    ) { hikes, liked, uid ->
        LikedHikesUiState(hikes = hikes, likedHikeIds = liked, currentUserUid = uid)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LikedHikesUiState(),
    )

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
                LikedHikesViewModel(
                    authRepository = app.container.authRepository,
                    hikeLogRepository = app.container.hikeLogRepository,
                    userRepository = app.container.userRepository,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedHikesRoute(
    onBack: () -> Unit,
    onHikeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: LikedHikesViewModel = viewModel(factory = LikedHikesViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liked hikes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        if (state.hikes.isEmpty()) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Text(
                    "Hikes you like will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            items(state.hikes, key = { it.hikeId }) { hike ->
                HikeCard(
                    hike = hike,
                    isLiked = hike.hikeId in state.likedHikeIds,
                    onClick = { onHikeClick(hike.hikeId) },
                    onLikeClick = { viewModel.toggleLike(hike) },
                    onCreatorClick = onUserClick,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
