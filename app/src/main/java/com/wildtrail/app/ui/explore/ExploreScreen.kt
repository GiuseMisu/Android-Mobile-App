package com.wildtrail.app.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wildtrail.app.domain.model.HikeFilter
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.SurfaceType
import com.wildtrail.app.ui.components.HikeCard
import com.wildtrail.app.ui.components.SectionHeader
import kotlin.math.roundToInt

@Composable
fun ExploreRoute(
    onHikeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExploreContent(
        state = state,
        onQueryChange = viewModel::onQueryChanged,
        onSortChange = viewModel::onSortChanged,
        onToggleFilterMenu = viewModel::onToggleFilterMenu,
        onDistanceChange = viewModel::onDistanceChange,
        onElevationChange = viewModel::onElevationChange,
        onDifficultyToggle = viewModel::onDifficultyToggle,
        onSurfaceTypeToggle = viewModel::onSurfaceTypeToggle,
        onResetFilters = viewModel::onResetFilters,
        onApplyFilters = viewModel::onApplyFilters,
        onHikeClick = onHikeClick,
        onUserClick = onUserClick,
        onRefresh = { viewModel.refresh() },
        onToggleLike = viewModel::toggleLike,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreContent(
    state: ExploreUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onToggleFilterMenu: () -> Unit,
    onDistanceChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onElevationChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onDifficultyToggle: (Int) -> Unit,
    onSurfaceTypeToggle: (SurfaceType) -> Unit,
    onResetFilters: () -> Unit,
    onApplyFilters: () -> Unit,
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Explore") }) },
    ) { padding: PaddingValues ->
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            placeholder = { Text("Search hikes…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onToggleFilterMenu) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filters",
                                tint = if (state.isFilterMenuOpen || state.appliedFilter.isActive()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SortOption.entries.forEach { option ->
                            FilterChip(
                                selected = state.sort == option,
                                onClick = { onSortChange(option) },
                                label = {
                                    Text(
                                        option.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = state.isFilterMenuOpen,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        FilterMenu(
                            filter = state.draftFilter,
                            onDistanceChange = onDistanceChange,
                            onElevationChange = onElevationChange,
                            onDifficultyToggle = onDifficultyToggle,
                            onSurfaceTypeToggle = onSurfaceTypeToggle,
                            onReset = onResetFilters,
                            onApply = onApplyFilters,
                        )
                    }
                }

                val list = if (state.showingResults) state.results else state.featured
                item {
                    SectionHeader(
                        if (state.showingResults) "Results" else "Featured this week",
                    )
                }
                if (list.isEmpty()) {
                    item {
                        Text(
                            if (state.showingResults) {
                                "No hikes match your search and filters."
                            } else {
                                "No featured hikes yet — be the first!"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(list, key = { it.hikeId }) { hike ->
                        HikeCard(
                            hike = hike,
                            isLiked = hike.hikeId in state.likedHikeIds,
                            onClick = { onHikeClick(hike.hikeId) },
                            onLikeClick = { onToggleLike(hike) },
                            onCreatorClick = onUserClick,
                            currentUserUid = state.currentUserUid,
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterMenu(
    filter: HikeFilter,
    onDistanceChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onElevationChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onDifficultyToggle: (Int) -> Unit,
    onSurfaceTypeToggle: (SurfaceType) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filters", style = MaterialTheme.typography.titleMedium)

            Text(
                "Distance: ${filter.minKm.roundToInt()}–${filter.maxKm.roundToInt()} km",
                style = MaterialTheme.typography.labelLarge,
            )
            RangeSlider(
                value = filter.minKm..filter.maxKm,
                onValueChange = onDistanceChange,
                valueRange = 0f..HikeFilter.DISTANCE_MAX_KM,
            )

            Text(
                "Elevation gain: ${filter.minElevation}–${filter.maxElevation} m",
                style = MaterialTheme.typography.labelLarge,
            )
            RangeSlider(
                value = filter.minElevation.toFloat()..filter.maxElevation.toFloat(),
                onValueChange = onElevationChange,
                valueRange = 0f..HikeFilter.ELEVATION_MAX_M.toFloat(),
            )

            Text("Difficulty", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HikeFilter.DIFFICULTY_LEVELS.forEach { level ->
                    FilterChip(
                        selected = level in filter.difficulties,
                        onClick = { onDifficultyToggle(level) },
                        label = { Text("$level") },
                    )
                }
            }

            Text("Surface type", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SurfaceType.entries.forEach { type ->
                    FilterChip(
                        selected = type in filter.surfaceTypes,
                        onClick = { onSurfaceTypeToggle(type) },
                        label = { Text(type.filterLabel()) },
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text("Apply")
                }
            }
        }
    }
}

private fun SurfaceType.filterLabel(): String = when (this) {
    SurfaceType.MOUNTAIN -> "Mountain"
    SurfaceType.FOREST -> "Forest"
    SurfaceType.COASTAL -> "Coastal"
    SurfaceType.URBAN -> "Urban"
    SurfaceType.DESERT -> "Desert"
    SurfaceType.OTHER -> "Other"
}
