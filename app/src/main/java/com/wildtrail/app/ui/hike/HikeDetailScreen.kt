package com.wildtrail.app.ui.hike

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.domain.model.HikeComment
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType
import com.wildtrail.app.domain.model.TrailReview
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.ui.components.FullScreenPhotoViewer
import com.wildtrail.app.ui.components.RatingGauge
import com.wildtrail.app.ui.components.StarRow
import com.wildtrail.app.util.AudioPlayerController
import com.wildtrail.app.util.BirdDetection
import com.wildtrail.app.util.PhotoDescriber
import com.wildtrail.app.util.formatHikeDate
import com.wildtrail.app.util.rememberAudioPlayerController
import com.wildtrail.app.util.rememberIsOnline
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun HikeDetailRoute(
    hikeId: String,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onAddReview: () -> Unit,
    viewModel: HikeDetailViewModel = viewModel(factory = HikeDetailViewModel.factory(hikeId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val predictionState by viewModel.predictionState.collectAsStateWithLifecycle()
    val summaryState by viewModel.summaryState.collectAsStateWithLifecycle()
    val isOnline = rememberIsOnline()
    val scope = rememberCoroutineScope()
    HikeDetailContent(
        state = state,
        predictionState = predictionState,
        summaryState = summaryState,
        isOnline = isOnline,
        onBack = onBack,
        onUserClick = onUserClick,
        onPredict = viewModel::requestPrediction,
        onPostComment = viewModel::postComment,
        onAddReview = onAddReview,
        onToggleLike = viewModel::toggleLike,
        onRefresh = { scope.launch { viewModel.refresh() } },
        onSummarizeReviews = viewModel::summarizeReviews,
        onDeleteHike = { viewModel.deleteHike(onDeleted = onBack) },
        onDeleteReview = viewModel::deleteReview,
        onDeleteComment = viewModel::deleteComment,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikeDetailContent(
    state: HikeDetailUiState,
    predictionState: PredictionState,
    summaryState: ReviewSummaryState,
    isOnline: Boolean,
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    onPredict: () -> Unit,
    onPostComment: (String) -> Unit,
    onAddReview: () -> Unit,
    onToggleLike: () -> Unit,
    onRefresh: () -> Unit,
    onSummarizeReviews: () -> Unit,
    onDeleteHike: () -> Unit,
    onDeleteReview: (String) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    var showDeleteHikeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (refreshing) {
            onRefresh()
            kotlinx.coroutines.delay(900L)
            refreshing = false
        }
    }

    if (showDeleteHikeConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteHikeConfirm = false },
            title = { Text("Delete hike?") },
            text = { Text("This permanently removes the hike and its photos/recordings for everyone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteHikeConfirm = false
                    onDeleteHike()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHikeConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.hike?.title ?: "Hike") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.currentUserUid != null) {
                        LikeButton(
                            liked = state.isLikedByMe,
                            count = state.likeCount,
                            onClick = onToggleLike,
                        )
                    }
                    if (state.isMyHike) {
                        IconButton(onClick = { showDeleteHikeConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete hike",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
    ) { padding: PaddingValues ->
        when {
            state.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Loading hike…")
                }
            }
            state.hike == null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "This hike could not be found.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> HikeDetailBody(
                state = state,
                padding = padding,
                refreshing = refreshing,
                onPullRefresh = { refreshing = true },
                onUserClick = onUserClick,
                onPostComment = onPostComment,
                onAddReview = onAddReview,
                predictionState = predictionState,
                summaryState = summaryState,
                isOnline = isOnline,
                onPredict = onPredict,
                onSummarizeReviews = onSummarizeReviews,
                onDeleteReview = onDeleteReview,
                onDeleteComment = onDeleteComment,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HikeDetailBody(
    state: HikeDetailUiState,
    padding: PaddingValues,
    refreshing: Boolean,
    onPullRefresh: () -> Unit,
    onUserClick: (String) -> Unit,
    onPostComment: (String) -> Unit,
    onAddReview: () -> Unit,
    predictionState: PredictionState,
    summaryState: ReviewSummaryState,
    isOnline: Boolean,
    onPredict: () -> Unit,
    onSummarizeReviews: () -> Unit,
    onDeleteReview: (String) -> Unit,
    onDeleteComment: (String) -> Unit,
) {
    val hike = state.hike!!
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onPullRefresh,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                CreatorBlock(
                    hike = hike,
                    creator = state.creator,
                    isMyHike = state.isMyHike,
                    onClick = { onUserClick(hike.creatorFirebaseUid) },
                )
            }
            if (hike.routeCoordinates.size >= 2) {
                item {
                    androidx.compose.material3.Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    ) {
                        com.wildtrail.app.ui.components.RouteMap(
                            points = hike.routeCoordinates,
                            modifier = Modifier.fillMaxSize(),
                            follow = false,
                            showCurrentMarker = false,
                            mediaItems = hike.mediaItems,
                        )
                    }
                }
                item { com.wildtrail.app.ui.components.ElevationChart(hike.routeCoordinates) }
            }
            if (hike.mediaItems.isNotEmpty()) {
                item { HikeMediaCard(hike.mediaItems) }
            }
            item { HikeStatsCard(hike) }
            item {
                PredictionCard(
                    predictionState = predictionState,
                    isOnline = isOnline,
                    onPredict = onPredict,
                )
            }
            item { CharacteristicsCard(hike) }
            val myReview = state.reviews.firstOrNull { it.reviewerUid == state.currentUserUid }
            val canAddReview =
                !state.isMyHike && state.currentUserUid != null && myReview == null
            item {
                if (canAddReview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ReviewStatsCard(hike, modifier = Modifier.weight(1f))
                        Button(
                            onClick = onAddReview,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "Add\nreview",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                } else {
                    ReviewStatsCard(hike, modifier = Modifier.fillMaxWidth())
                }
            }

            if (state.isMyHike) {
                item {
                    Text(
                        "You can't review your own hike — the trail characteristics " +
                            "you filled in at save time are shown above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (myReview != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Your review",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ReviewRow(
                            review = myReview,
                            author = state.currentUserUid?.let { state.authors[it] },
                            onAuthorClick = { state.currentUserUid?.let(onUserClick) },
                            highlighted = true,
                            onDelete = { onDeleteReview(myReview.reviewId) },
                        )
                    }
                }
            }

            val hasWrittenReviews = state.reviews.any { !it.commentText.isNullOrBlank() }
            if (hasWrittenReviews) {
                item {
                    ReviewSummaryCard(
                        state = summaryState,
                        onSummarize = onSummarizeReviews,
                    )
                }
            }

            val otherReviews = state.reviews.filter { it.reviewerUid != state.currentUserUid }
            item {
                Text(
                    "Reviews (${otherReviews.size})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (otherReviews.isEmpty()) {
                item {
                    Text(
                        if (state.myReviewExists || state.isMyHike) {
                            "No other reviews yet."
                        } else {
                            "No reviews yet — be the first to share your experience!"
                        },
                    )
                }
            } else {
                items(otherReviews, key = { it.reviewId }) { review ->
                    ReviewRow(
                        review = review,
                        author = state.authors[review.reviewerUid],
                        onAuthorClick = { onUserClick(review.reviewerUid) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Text("Comments", style = MaterialTheme.typography.titleLarge)
            }
            items(state.comments, key = { it.commentId }) { c ->
                CommentRow(
                    comment = c,
                    author = state.authors[c.authorUid],
                    onAuthorClick = { onUserClick(c.authorUid) },
                    onDelete = if (c.authorUid == state.currentUserUid) {
                        { onDeleteComment(c.commentId) }
                    } else {
                        null
                    },
                )
            }
            item { CommentForm(onPost = onPostComment) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LikeButton(liked: Boolean, count: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = if (liked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("$count", modifier = Modifier.padding(end = 8.dp))
    }
}

@Composable
private fun CreatorBlock(
    hike: HikeLog,
    creator: User?,
    isMyHike: Boolean,
    onClick: () -> Unit,
) {
    val displayName = when {
        isMyHike -> "You"
        hike.creatorUsername.isNotBlank() -> hike.creatorUsername
        creator != null && creator.username.isNotBlank() -> creator.username
        else -> "Hiker"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarFromUrl(
                url = hike.creatorProfilePictureUrl ?: creator?.profilePictureUrl,
                size = 40.dp,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    "Posted by $displayName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (creator != null) {
                    Text(
                        "Level ${creator.level} · ${creator.xpPoints} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (!isMyHike) {
                    Text(
                        "Tap to view profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarFromUrl(url: String?, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(size),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PredictionCard(
    predictionState: PredictionState,
    isOnline: Boolean,
    onPredict: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("AI Time Forecast", style = MaterialTheme.typography.titleMedium)

            when (predictionState) {
                PredictionState.Idle -> {
                    if (!isOnline) {
                        Text(
                            "Internet connection required to predict",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onPredict,
                        enabled = isOnline,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Predict My Time")
                    }
                }

                PredictionState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            "Calculating your estimated time…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                is PredictionState.Success -> {
                    val totalMinutes = predictionState.minutes
                    val hours = (totalMinutes / 60).toInt()
                    val mins  = (totalMinutes % 60).toInt()
                    val formatted = if (hours > 0) "${hours}h ${mins}min" else "${mins}min"

                    Text(
                        "Your estimated time:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatted,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is PredictionState.Error -> {
                    Text(
                        predictionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(
                        onClick = onPredict,
                        enabled = isOnline,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun HikeMediaCard(items: List<HikeMediaItem>) {
    val photos = items.filter { it.type == HikeMediaType.PHOTO }
        .sortedBy { it.timestamp }
    val audios = items.filter { it.type == HikeMediaType.AUDIO }
        .sortedBy { it.timestamp }
    val audioController = rememberAudioPlayerController()

    var openedPhotoIndex by remember { mutableStateOf<Int?>(null) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Captured on the trail (${items.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                        PhotoThumbnail(
                            photo = photo,
                            number = index + 1,
                            onClick = { openedPhotoIndex = index },
                        )
                    }
                }
            }
            if (audios.isNotEmpty()) {
                audios.forEachIndexed { index, audio ->
                    AudioRow(audio = audio, number = index + 1, controller = audioController)
                }
            }
        }
    }

    openedPhotoIndex?.let { idx ->
        PhotoViewerDialog(
            photo = photos[idx],
            number = idx + 1,
            onDismiss = { openedPhotoIndex = null },
        )
    }
}

@Composable
private fun PhotoThumbnail(
    photo: HikeMediaItem,
    number: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photoModel(photo.filePath),
            contentDescription = "Photo $number",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "Photo $number",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topEnd = 8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PhotoViewerDialog(
    photo: HikeMediaItem,
    number: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as WildTrailApp).container }
    val describer: PhotoDescriber = remember { container.photoDescriber }
    val mediaStore = remember { container.hikeMediaStore }

    var description by remember(photo.id) { mutableStateOf<String?>(null) }
    var failed by remember(photo.id) { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        description = null
        failed = false
        runCatching { describer.describe(mediaStore.localFileFor(photo)) }
            .onSuccess { description = it }
            .onFailure { failed = true }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Photo $number · ${formatHikeDate(photo.timestamp)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                        )
                    }
                }

                AsyncImage(
                    model = photoModel(photo.filePath),
                    contentDescription = "Photo $number",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                )

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "AI photo description",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        when {
                            description != null -> Text(
                                description!!,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            failed -> Text(
                                "Couldn't analyse this photo.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Analysing…",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        Text(
                            "Location: %.5f, %.5f".format(photo.lat, photo.lng),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface BirdScanState {
    data object Idle : BirdScanState
    data object Scanning : BirdScanState
    data class Found(val birds: List<BirdDetection>) : BirdScanState
    data object None : BirdScanState
    data object NoModel : BirdScanState
    data object Failed : BirdScanState
}

// Coil loads an http URL (remote media) directly; a bare on-device path needs a File.
private fun photoModel(filePath: String): Any =
    if (filePath.startsWith("http")) filePath else File(filePath)

@Composable
private fun AudioRow(audio: HikeMediaItem, number: Int, controller: AudioPlayerController) {
    val isPlaying = controller.playingPath == audio.filePath
    val context = LocalContext.current
    val container = remember { (context.applicationContext as WildTrailApp).container }
    val classifier = remember { container.birdNetClassifier }
    val mediaStore = remember { container.hikeMediaStore }
    val scope = rememberCoroutineScope()
    var scan by remember(audio.id) { mutableStateOf<BirdScanState>(BirdScanState.Idle) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = { controller.toggle(audio.filePath) }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Voice $number", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatHikeDate(audio.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = {
                    if (scan !is BirdScanState.Scanning) {
                        if (!classifier.isModelInstalled()) {
                            scan = BirdScanState.NoModel
                        } else {
                            scan = BirdScanState.Scanning
                            scope.launch {
                                scan = runCatching { classifier.detect(mediaStore.localFileFor(audio)) }.fold(
                                    onSuccess = { birds ->
                                        if (birds.isEmpty()) BirdScanState.None
                                        else BirdScanState.Found(birds)
                                    },
                                    onFailure = { BirdScanState.Failed },
                                )
                            }
                        }
                    }
                },
                enabled = scan !is BirdScanState.Scanning,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (scan is BirdScanState.Scanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        "Detect\nBird",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        when (val s = scan) {
            is BirdScanState.Found -> {
                val top = s.birds.first()
                Text(
                    "🐦 ${top.commonName} · ${(top.confidence * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
                )
            }
            BirdScanState.None -> Text(
                "No confident bird detected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
            )
            BirdScanState.NoModel -> Text(
                "Bird model not installed — add it to assets/birdnet/",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
            )
            BirdScanState.Failed -> Text(
                "Couldn't analyse this recording",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp),
            )
            else -> {}
        }
    }
}

@Composable
private fun HikeStatsCard(hike: HikeLog) {
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Recorded on ${formatHikeDate(hike.endedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hike.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Distance", "%.1f km".format(hike.lengthKm))
                Stat("Elev. gain", "${hike.elevationGainMeters} m")
                Stat("Avg speed", "%.1f km/h".format(hike.avgSpeedKmh))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Calories", "${hike.caloriesBurned} kcal")
                Stat("XP", "${hike.xpEarned}")
                Stat("Surface", hike.surfaceType.name)
            }
        }
    }
}

@Composable
private fun CharacteristicsCard(hike: HikeLog) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Trail characteristics (from creator)",
                style = MaterialTheme.typography.titleMedium,
            )
            CharacteristicLine("Difficulty", hike.difficultyLevel)
            CharacteristicLine("Mud risk", hike.mudRisk)
            CharacteristicLine("Path clarity", hike.pathClarity)
            CharacteristicLine("Fatigue", hike.fatigueLevel)
            CharacteristicLine("Animal risk", hike.animalEncounterRisk)
            Text(
                if (hike.waterAvailability) "💧 Water available on trail" else "🚱 No water sources",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun CharacteristicLine(label: String, value: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        StarRow(rating = value.toFloat())
        Text("  $value/5", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReviewSummaryCard(
    state: ReviewSummaryState,
    onSummarize: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI review summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                when (state) {
                    ReviewSummaryState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                    )
                    is ReviewSummaryState.Success -> Unit
                    else -> Button(onClick = onSummarize) { Text("AI summary") }
                }
            }

            when (state) {
                is ReviewSummaryState.Success -> Text(
                    state.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                is ReviewSummaryState.Error -> Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                ReviewSummaryState.Loading -> Text(
                    "Reading what hikers said…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                ReviewSummaryState.Idle -> Text(
                    "Get the gist of all reviews for this trail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun ReviewStatsCard(hike: HikeLog, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        if (hike.reviewCount == 0) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Community rating",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "No reviews yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RatingGauge(
                    rating = hike.averageRating,
                    modifier = Modifier.size(76.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Community rating",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StarRow(rating = hike.averageRating, size = 18.dp)
                    Text(
                        "%.1f / 5 · %d review%s".format(
                            hike.averageRating,
                            hike.reviewCount,
                            if (hike.reviewCount == 1) "" else "s",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReviewRow(
    review: TrailReview,
    author: User?,
    onAuthorClick: () -> Unit,
    highlighted: Boolean = false,
    onDelete: (() -> Unit)? = null,
) {
    var openedPhoto by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete review?") },
            text = { Text("This permanently removes your review. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = if (highlighted) 3.dp else 1.dp,
        ),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AvatarFromUrl(url = author?.profilePictureUrl, size = 44.dp)
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .weight(1f)
                        .clickable(onClick = onAuthorClick),
                ) {
                    Text(
                        author?.username ?: "Unknown user",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        com.wildtrail.app.util.formatHikeDate(review.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ScoreBadge(rating = review.overallRating)
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete review",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ReviewPill("Difficulty", "${review.difficultyLevel}/5")
                ReviewPill("Mud", "${review.mudRisk}/5")
                ReviewPill("Path", "${review.pathClarity}/5")
                ReviewPill("Fatigue", "${review.fatigueLevel}/5")
                ReviewPill("Animal risk", "${review.animalEncounterRisk}/5")
                ReviewPill(
                    label = if (review.waterAvailability) "💧 Water" else "🚱 No water",
                    value = null,
                )
            }

            val comment = review.commentText
            if (!comment.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(comment, style = MaterialTheme.typography.bodyMedium)
            }

            if (review.imageUrls.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(review.imageUrls, key = { _, url -> url }) { index, url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Review photo ${index + 1} — tap to view",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { openedPhoto = index },
                        )
                    }
                }
            }
        }
    }

    openedPhoto?.let { idx ->
        FullScreenPhotoViewer(
            imageUrls = review.imageUrls,
            startIndex = idx,
            onDismiss = { openedPhoto = null },
        )
    }
}

@Composable
private fun ScoreBadge(rating: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "$rating/5",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ReviewPill(label: String, value: String?) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (value != null) {
                Spacer(Modifier.size(6.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: HikeComment,
    author: User?,
    onAuthorClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete comment?") },
            text = { Text("This permanently removes your comment.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAuthorClick),
                ) {
                    AvatarFromUrl(url = author?.profilePictureUrl, size = 32.dp)
                    Text(
                        author?.username ?: "Unknown user",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete comment",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CommentForm(onPost: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Add a comment…") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onPost(text)
                        text = ""
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Post") }
        }
    }
}
