package com.wildtrail.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.ui.components.AuroraHeader
import com.wildtrail.app.ui.components.HeroStat
import com.wildtrail.app.ui.components.HikeCard
import com.wildtrail.app.ui.components.SectionHeader

@Composable
fun HomeRoute(
    onHikeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onHikeClick = onHikeClick,
        onUserClick = onUserClick,
        onRefresh = { viewModel.refresh() },
        onToggleLike = viewModel::toggleLike,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onHikeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onRefresh: suspend () -> Unit,
    onToggleLike: (HikeLog) -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            onRefresh()
            kotlinx.coroutines.delay(900L)
            refreshing = false
        }
    }

    Scaffold { padding: PaddingValues ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HomeHero(state.currentUser)
                }
                item { SectionHeader("Recently from you") }
                if (state.recentHikes.isEmpty()) {
                    item {
                        Text(
                            "Your hikes will appear here once you record one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.recentHikes, key = { "mine-${it.hikeId}" }) { hike ->
                        HikeCard(
                            hike = hike,
                            isLiked = hike.hikeId in state.likedHikeIds,
                            onClick = { onHikeClick(hike.hikeId) },
                            onLikeClick = { onToggleLike(hike) },
                            onCreatorClick = onUserClick,
                            currentUserUid = state.currentUser?.firebaseUid,
                            currentUserProfilePictureUrl = state.currentUser?.profilePictureUrl,
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    SectionHeader("Trending publicly")
                }
                val mineIds = state.recentHikes.mapTo(HashSet()) { it.hikeId }
                val publicOthers = state.publicFeed.filter { it.hikeId !in mineIds }
                items(publicOthers, key = { "public-${it.hikeId}" }) { hike ->
                    HikeCard(
                        hike = hike,
                        isLiked = hike.hikeId in state.likedHikeIds,
                        onClick = { onHikeClick(hike.hikeId) },
                        onLikeClick = { onToggleLike(hike) },
                        onCreatorClick = onUserClick,
                        currentUserUid = state.currentUser?.firebaseUid,
                        currentUserProfilePictureUrl = state.currentUser?.profilePictureUrl,
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun HomeHero(user: User?) {
    AuroraHeader(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Column {
                Text(
                    "WELCOME BACK",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    user?.username ?: "Welcome",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            if (user != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        HeroStat(user.totalHikesCount.toString(), "Hikes", Modifier.weight(1f))
                        HeroStat("%.0f km".format(user.totalDistanceKm), "Distance", Modifier.weight(1f))
                        HeroStat(user.xpPoints.toString(), "XP", Modifier.weight(1f))
                        HeroStat("Lv ${user.level}", "Level", Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
