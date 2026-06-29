package com.wildtrail.app.ui.review

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.wildtrail.app.ui.components.AuroraHeader
import com.wildtrail.app.ui.components.EditableStarRow
import com.wildtrail.app.ui.components.FullScreenPhotoViewer
import com.wildtrail.app.ui.components.RatingRow

private const val MAX_REVIEW_PHOTOS = 5

@Composable
fun SubmitReviewRoute(
    hikeId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: SubmitReviewViewModel = viewModel(factory = SubmitReviewViewModel.factory(hikeId)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.submitted) {
        if (state.submitted) onDone()
    }

    SubmitReviewContent(
        state = state,
        onBack = onBack,
        onOverallChange = viewModel::onOverallChange,
        onDifficultyChange = viewModel::onDifficultyChange,
        onMudChange = viewModel::onMudChange,
        onClarityChange = viewModel::onClarityChange,
        onFatigueChange = viewModel::onFatigueChange,
        onAnimalChange = viewModel::onAnimalChange,
        onWaterChange = viewModel::onWaterChange,
        onCommentChange = viewModel::onCommentChange,
        onAddImages = viewModel::onAddImages,
        onRemoveImage = viewModel::onRemoveImage,
        onErrorShown = viewModel::onErrorShown,
        onSubmit = viewModel::submit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitReviewContent(
    state: SubmitReviewUiState,
    onBack: () -> Unit,
    onOverallChange: (Int) -> Unit,
    onDifficultyChange: (Int) -> Unit,
    onMudChange: (Int) -> Unit,
    onClarityChange: (Int) -> Unit,
    onFatigueChange: (Int) -> Unit,
    onAnimalChange: (Int) -> Unit,
    onWaterChange: (Boolean) -> Unit,
    onCommentChange: (String) -> Unit,
    onAddImages: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit,
    onErrorShown: () -> Unit,
    onSubmit: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Write a review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            AuroraHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(212.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "How was the trail?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(10.dp))
                    EditableStarRow(rating = state.overallRating, onChange = onOverallChange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${ratingWord(state.overallRating)} · ${state.overallRating}/5",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            SectionCard(title = "Trail conditions") {
                RatingRow("Difficulty", state.difficultyLevel, onDifficultyChange)
                RatingRow("Mud risk", state.mudRisk, onMudChange)
                RatingRow("Path clarity", state.pathClarity, onClarityChange)
                RatingRow("Fatigue", state.fatigueLevel, onFatigueChange)
                RatingRow("Animal risk", state.animalEncounterRisk, onAnimalChange)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        "  Water sources available",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = state.waterAvailability, onCheckedChange = onWaterChange)
                }
            }

            SectionCard(title = "Your feedback") {
                OutlinedTextField(
                    value = state.commentText,
                    onValueChange = onCommentChange,
                    placeholder = { Text("How was the trail? Anything fellow hikers should know?") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = "Photos") {
                PhotoPickerSection(
                    imageUris = state.imageUris,
                    onAddImages = onAddImages,
                    onRemoveImage = onRemoveImage,
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Uploading…")
                } else {
                    Text("Upload review")
                }
            }
            TextButton(
                onClick = onBack,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun ratingWord(rating: Int): String = when (rating) {
    5 -> "Outstanding"
    4 -> "Great"
    3 -> "Good"
    2 -> "Okay"
    else -> "Needs work"
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun PhotoPickerSection(
    imageUris: List<Uri>,
    onAddImages: (List<Uri>) -> Unit,
    onRemoveImage: (Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_REVIEW_PHOTOS),
    ) { uris: List<Uri> -> if (uris.isNotEmpty()) onAddImages(uris) }

    var previewIndex by remember { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            enabled = imageUris.size < MAX_REVIEW_PHOTOS,
        ) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (imageUris.isEmpty()) "Add photos" else "Add more (${imageUris.size}/$MAX_REVIEW_PHOTOS)")
        }
        if (imageUris.isEmpty()) {
            Text(
                "Optional — a couple of shots help fellow hikers picture the trail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = imageUris, key = { it.toString() }) { uri ->
                    ReviewPhotoThumbnail(
                        uri = uri,
                        onOpen = { previewIndex = imageUris.indexOf(uri) },
                        onRemove = { onRemoveImage(uri) },
                    )
                }
            }
        }
    }

    previewIndex?.let { idx ->
        FullScreenPhotoViewer(
            imageUrls = imageUris.map { it.toString() },
            startIndex = idx,
            onDismiss = { previewIndex = null },
        )
    }
}

@Composable
private fun ReviewPhotoThumbnail(
    uri: Uri,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Box(modifier = Modifier.size(104.dp)) {
        AsyncImage(
            model = uri,
            contentDescription = "Selected photo — tap to preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpen),
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove photo",
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(2.dp),
            )
        }
    }
}
