package com.wildtrail.app.ui.profile

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wildtrail.app.domain.model.AchievementDefinition
import com.wildtrail.app.domain.model.DEFAULT_EMERGENCY_NUMBER
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.ui.components.AuroraHeader
import com.wildtrail.app.ui.components.HikeCard
import com.wildtrail.app.ui.components.SectionHeader
import com.wildtrail.app.util.LevelMath
import java.util.Calendar
import java.util.Locale

@Composable
fun ProfileRoute(
    targetUid: String? = null,
    onBack: (() -> Unit)? = null,
    onHikeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onOpenLiked: (() -> Unit)? = null,
    onOpenAchievements: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel(
        key = "profile/${targetUid ?: "me"}",
        factory = ProfileViewModel.factory(targetUid),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onBack = onBack,
        onHikeClick = onHikeClick,
        onUserClick = onUserClick,
        onSignOut = viewModel::signOut,
        onRefresh = { viewModel.refresh() },
        onToggleLike = viewModel::toggleLike,
        onOpenSettings = onOpenSettings,
        onOpenLiked = onOpenLiked,
        onOpenAchievements = onOpenAchievements,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileContent(
    state: ProfileUiState,
    onBack: (() -> Unit)?,
    onHikeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onRefresh: suspend () -> Unit,
    onToggleLike: (HikeLog) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    onOpenLiked: (() -> Unit)? = null,
    onOpenAchievements: (() -> Unit)? = null,
) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            onRefresh()
            kotlinx.coroutines.delay(900L)
            refreshing = false
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isMe) "Profile" else state.user?.username ?: "Profile") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (state.isMe) {
                        if (onOpenLiked != null) {
                            IconButton(onClick = onOpenLiked) {
                                Icon(Icons.Filled.Public, contentDescription = "Liked hikes")
                            }
                        }
                        if (onOpenAchievements != null) {
                            IconButton(onClick = onOpenAchievements) {
                                Icon(
                                    Icons.Filled.EmojiEvents,
                                    contentDescription = "Achievements",
                                )
                            }
                        }
                        if (onOpenSettings != null) {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.Filled.Logout, contentDescription = "Sign out")
                        }
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { state.user?.let { ProfileHeader(it) } }
                item {
                    state.user?.let {
                        LevelProgressCard(it, state.earnedAchievements.size)
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    val openAch = onOpenAchievements
                    SectionHeader(
                        title = "Recent Achievements",
                        trailing = if (openAch != null) {
                            { TextButton(onClick = openAch) { Text("See all") } }
                        } else {
                            null
                        },
                    )
                }
                if (state.earnedAchievements.isEmpty()) {
                    item {
                        Text(
                            "No achievements yet. Hike more to unlock them!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(
                        state.earnedAchievements.take(2),
                        key = { it.achievementId },
                    ) { achievement ->
                        AchievementRow(achievement)
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader(if (state.isMe) "My hikes" else "Hikes")
                }
                if (state.hikes.isEmpty()) {
                    item {
                        Text(
                            if (state.isMe) "Your hikes will appear here." else "No public hikes yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.hikes, key = { it.hikeId }) { hike ->
                        HikeCard(
                            hike = hike,
                            isLiked = hike.hikeId in state.likedHikeIds,
                            onClick = { onHikeClick(hike.hikeId) },
                            onLikeClick = { onToggleLike(hike) },
                            onCreatorClick = onUserClick,
                            currentUserUid = state.currentUserUid,
                            currentUserProfilePictureUrl = state.user?.profilePictureUrl,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: User) {
    AuroraHeader(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user.profilePictureUrl != null) {
                        AsyncImage(
                            model = user.profilePictureUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        user.username,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    val ageText = user.dateOfBirth?.let { dob ->
                        val years = ageInYears(dob)
                        if (years > 0) "$years y/o" else null
                    }
                    val parts = listOfNotNull(
                        user.country,
                        ageText,
                        user.sex?.name?.lowercase(Locale.getDefault())?.replace('_', ' '),
                    )
                    if (parts.isNotEmpty()) {
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
            }
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                )
            }
            val number = user.emergencyContactNumber.ifBlank { DEFAULT_EMERGENCY_NUMBER }
            val isDefault = number == DEFAULT_EMERGENCY_NUMBER
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isDefault) "Emergency contact: $number (default)" else "Emergency contact: $number",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun LevelProgressCard(user: User, achievementsCount: Int) {
    val level = LevelMath.levelForXp(user.xpPoints)
    val current = LevelMath.xpInCurrentLevel(user.xpPoints)
    val needed = LevelMath.xpForNextLevel(level)
    val progress = LevelMath.progressInCurrentLevel(user.xpPoints)

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Level $level",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${user.xpPoints} XP total",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$current / $needed XP toward Level ${level + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                StatBlock("Hikes", user.totalHikesCount.toString())
                StatBlock("Distance", "%.1f km".format(user.totalDistanceKm))
                StatBlock("Achievements", achievementsCount.toString())
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AchievementRow(achievement: AchievementDefinition) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(achievement.name, style = MaterialTheme.typography.titleMedium)
                Text(achievement.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun ageInYears(dobMillis: Long): Int {
    val today = Calendar.getInstance()
    val dob = Calendar.getInstance().apply { timeInMillis = dobMillis }
    var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
    if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
    return age.coerceAtLeast(0)
}
