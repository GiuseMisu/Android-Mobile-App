package com.wildtrail.app.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.ui.components.AuroraHeader
import com.wildtrail.app.ui.components.SectionHeader
import com.wildtrail.app.util.AchievementEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.min

data class AchievementsUiState(
    val definitions: List<AchievementDefinition> = emptyList(),
    val earnedIds: Set<String> = emptySet(),
    val user: User? = null,
    val hikes: List<HikeLog> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val hikeLogRepository: HikeLogRepository,
    private val achievementRepository: AchievementRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { runCatching { achievementRepository.syncDefinitions() } }
    }

    private val currentUid = authRepository.authState
        .map { (it as? AuthState.SignedIn)?.user?.firebaseUid }

    val uiState: StateFlow<AchievementsUiState> = currentUid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(AchievementsUiState())
            else combine(
                achievementRepository.observeAll(),
                achievementRepository.observeEarned(uid).map { list -> list.map { it.achievementId }.toSet() },
                userRepository.observeUser(uid),
                hikeLogRepository.observeMyHikes(uid),
            ) { defs, earned, user, hikes ->
                AchievementsUiState(
                    definitions = defs,
                    earnedIds = earned,
                    user = user,
                    hikes = hikes,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AchievementsUiState(),
        )

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                AchievementsViewModel(
                    authRepository = app.container.authRepository,
                    userRepository = app.container.userRepository,
                    hikeLogRepository = app.container.hikeLogRepository,
                    achievementRepository = app.container.achievementRepository,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsRoute(
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = viewModel(factory = AchievementsViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Achievements (${state.earnedIds.size}/${state.definitions.size})")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        if (state.definitions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Loading achievements…") }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                AchievementsHero(
                    earned = state.earnedIds.size,
                    total = state.definitions.size,
                )
            }
            val (earned, locked) = state.definitions.partition { it.achievementId in state.earnedIds }
            if (earned.isNotEmpty()) {
                item { SectionHeader("Unlocked") }
                items(earned, key = { "earned-${it.achievementId}" }) { def ->
                    AchievementRow(definition = def, earned = true, user = state.user, hikes = state.hikes)
                }
            }
            if (locked.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    SectionHeader("Locked")
                }
                items(locked, key = { "locked-${it.achievementId}" }) { def ->
                    AchievementRow(definition = def, earned = false, user = state.user, hikes = state.hikes)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AchievementsHero(earned: Int, total: Int) {
    AuroraHeader(
        modifier = Modifier
            .fillMaxWidth()
            .height(156.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "YOUR TROPHIES",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                "$earned of $total unlocked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            val fraction = if (total > 0) earned.toFloat() / total.toFloat() else 0f
            LinearProgressIndicator(
                progress = { fraction },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            )
        }
    }
}

@Composable
private fun AchievementRow(
    definition: AchievementDefinition,
    earned: Boolean,
    user: User?,
    hikes: List<HikeLog>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (earned) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = if (earned) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(36.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
                Text(definition.name, style = MaterialTheme.typography.titleMedium)
                Text(definition.description, style = MaterialTheme.typography.bodySmall)
                if (!earned && user != null) {
                    val metric = AchievementEngine.metricFor(definition.category, user, hikes)
                    val target = definition.thresholdValue
                    val progress = if (target <= 0f) 0f
                    else min(1f, metric / target)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${formatMetric(metric)} / ${formatMetric(target)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "+${definition.xpReward} XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (earned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatMetric(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
