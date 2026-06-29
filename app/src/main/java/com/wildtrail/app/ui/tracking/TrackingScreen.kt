package com.wildtrail.app.ui.tracking

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType
import com.wildtrail.app.domain.model.WeatherPoint
import com.wildtrail.app.domain.model.WeatherSnapshot
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.wildtrail.app.domain.model.SurfaceType
import com.wildtrail.app.ui.components.RatingRow
import java.io.File

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackingRoute(
    trackingViewModel: TrackingViewModel = viewModel(factory = TrackingViewModel.factory()),
    weatherViewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.factory()),
) {
    val state by trackingViewModel.uiState.collectAsStateWithLifecycle()
    val weatherState by weatherViewModel.uiState.collectAsStateWithLifecycle()

    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
    )

    LaunchedEffect(permissions.allPermissionsGranted) {
        trackingViewModel.onPermissionResult(permissions.allPermissionsGranted)
    }

    val context = LocalContext.current

    // Lets the foreground-service notification show on Android 13+. The service still
    // records GPS even if this is denied — only the ongoing notification is suppressed.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        trackingViewModel.events.collect { event ->
            when (event) {
                TrackingEvent.ShowEmergencyOverlay -> Unit
                is TrackingEvent.EmergencyCallPlaced ->
                    Toast.makeText(
                        context,
                        "Emergency Call Initiated to ${event.contactName}",
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    var autoWeatherRefreshDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.currentLocation?.lat, state.currentLocation?.lng) {
        val location = state.currentLocation ?: return@LaunchedEffect
        weatherViewModel.onCoordinatesUpdated(latitude = location.lat, longitude = location.lng)
        if (!autoWeatherRefreshDone) {
            autoWeatherRefreshDone = true
            weatherViewModel.refreshWeather()
        }
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    if (state.status == TrackingStatus.STOPPED && !state.saved) {
        LaunchedEffect(Unit) { showSaveDialog = true }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Track a hike") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeatherSummaryCard(
                state = weatherState,
                onRefresh = weatherViewModel::refreshWeather,
            )
            if (!state.hasPermission) {
                PermissionBanner(onRequest = { permissions.launchMultiplePermissionRequest() })
            }
            if (state.hasPermission) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                ) {
                    com.wildtrail.app.ui.components.RouteMap(
                        points = state.routePoints,
                        modifier = Modifier.fillMaxSize(),
                        follow = state.status == TrackingStatus.RECORDING ||
                            state.status == TrackingStatus.PAUSED,
                        showCurrentMarker = true,
                        currentLocation = state.currentLocation,
                    )
                }
            }
            StatsCard(state)
            Spacer(Modifier.height(8.dp))
            ControlButtons(
                status = state.status,
                hasPermission = state.hasPermission,
                onStart = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    weatherViewModel.refreshWeather()
                    trackingViewModel.start()
                },
                onPause = trackingViewModel::pause,
                onResume = trackingViewModel::resume,
                onStop = trackingViewModel::stop,
            )

            if (state.status == TrackingStatus.RECORDING ||
                state.status == TrackingStatus.PAUSED
            ) {
                MediaCaptureRow(
                    isRecordingAudio = state.isRecordingAudio,
                    onPhoto = trackingViewModel::capturePhoto,
                    onStartAudio = trackingViewModel::startAudioRecording,
                    onStopAudio = trackingViewModel::stopAudioRecording,
                )
                if (state.mediaItems.isNotEmpty()) {
                    CapturedMediaStrip(items = state.mediaItems)
                }
            }

            state.errorMessage?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showSaveDialog) {
        SaveHikeDialog(
            distanceKm = state.distanceKm,
            onConfirm = { req ->
                trackingViewModel.saveHike(
                    title = req.title,
                    description = req.description,
                    surfaceType = req.surfaceType,
                    isPrivate = req.isPrivate,
                    difficultyLevel = req.difficultyLevel,
                    mudRisk = req.mudRisk,
                    pathClarity = req.pathClarity,
                    fatigueLevel = req.fatigueLevel,
                    animalEncounterRisk = req.animalEncounterRisk,
                    waterAvailability = req.waterAvailability,
                )
                showSaveDialog = false
            },
            onDismiss = {
                showSaveDialog = false
                trackingViewModel.resetAfterSave()
            },
        )
    }

    if (state.saved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            trackingViewModel.resetAfterSave()
        }
    }

    state.emergency?.let { emergency ->
        EmergencyOverlay(
            contactName = emergency.contactName,
            countdownSeconds = emergency.countdownSeconds,
            onCancel = trackingViewModel::onEmergencyCancelled,
            onCountdownFinished = trackingViewModel::onEmergencyCountdownFinished,
        )
    }
}

@Composable
private fun PermissionBanner(onRequest: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Location permission is required to record hikes.",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequest) { Text("Grant permission") }
        }
    }
}

@Composable
private fun WeatherSummaryCard(
    state: WeatherUiState,
    onRefresh: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, end = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Weather",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 2.dp),
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(22.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh weather",
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            when (state) {
                is WeatherUiState.Loading -> {
                    Text(
                        text = "Loading weather...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.lastKnown?.let {
                        WeatherTriplet(weather = it)
                    }
                }

                is WeatherUiState.Success -> {
                    if (state.fromCache) {
                        Text(
                            text = "Cached weather",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    WeatherTriplet(weather = state.weather)
                }

                is WeatherUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.lastKnown?.let {
                        WeatherTriplet(weather = it)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherTriplet(weather: WeatherSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WeatherPointItem(
            label = "Now",
            point = weather.current,
            modifier = Modifier.weight(1f),
        )
        WeatherPointItem(
            label = "+1h",
            point = weather.plus1h,
            modifier = Modifier.weight(1f),
        )
        WeatherPointItem(
            label = "+2h",
            point = weather.plus2h,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WeatherPointItem(
    label: String,
    point: WeatherPoint,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = weatherEmoji(point.iconId),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "%.0f°".format(point.temperatureC),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = point.description.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun weatherEmoji(iconId: String): String = when (iconId) {
    "01d" -> "☀️"
    "01n" -> "🌙"
    "02d", "02n" -> "🌤️"
    "03d", "03n", "04d", "04n" -> "☁️"
    "09d", "09n" -> "🌧️"
    "10d" -> "🌦️"
    "10n" -> "🌧️"
    "11d", "11n" -> "⛈️"
    "13d", "13n" -> "❄️"
    "50d", "50n" -> "🌫️"
    else -> "🌡️"
}

@Composable
private fun StatsCard(state: TrackingUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Distance", "%.2f km".format(state.distanceKm))
                Stat("Time", formatDuration(state.durationSec))
                Stat("Speed", "%.1f km/h".format(state.avgSpeedKmh))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Stat("Elev. gain", "${state.elevationGainM} m")
                Stat("Points", state.routePoints.size.toString())
                Stat("Status", state.status.name)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ControlButtons(
    status: TrackingStatus,
    hasPermission: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    when (status) {
        TrackingStatus.IDLE -> Button(
            onClick = onStart,
            enabled = hasPermission,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("Start hike", style = MaterialTheme.typography.titleMedium) }

        TrackingStatus.RECORDING -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Pause")
            }
            Button(onClick = onStop, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Finish")
            }
        }

        TrackingStatus.PAUSED -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onResume, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Resume")
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("Finish")
            }
        }

        TrackingStatus.STOPPED -> Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Hike finished — fill in the details to save",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private data class SaveHikeRequest(
    val title: String,
    val description: String?,
    val surfaceType: SurfaceType,
    val isPrivate: Boolean,
    val difficultyLevel: Int,
    val mudRisk: Int,
    val pathClarity: Int,
    val fatigueLevel: Int,
    val animalEncounterRisk: Int,
    val waterAvailability: Boolean,
)

@Composable
private fun SaveHikeDialog(
    distanceKm: Float,
    onConfirm: (SaveHikeRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    var surface by remember { mutableStateOf(SurfaceType.MOUNTAIN) }

    var difficulty by remember { mutableStateOf(3) }
    var mud by remember { mutableStateOf(3) }
    var clarity by remember { mutableStateOf(3) }
    var fatigue by remember { mutableStateOf(3) }
    var animal by remember { mutableStateOf(3) }
    var water by remember { mutableStateOf(false) }

    val isFormValid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                enabled = isFormValid,
                onClick = {
                    onConfirm(
                        SaveHikeRequest(
                            title = title.trim(),
                            description = description.takeIf { it.isNotBlank() },
                            surfaceType = surface,
                            isPrivate = isPrivate,
                            difficultyLevel = difficulty,
                            mudRisk = mud,
                            pathClarity = clarity,
                            fatigueLevel = fatigue,
                            animalEncounterRisk = animal,
                            waterAvailability = water,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Discard") } },
        title = { Text("Save hike (${"%.2f".format(distanceKm)} km)") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    isError = title.isBlank(),
                    supportingText = {
                        if (title.isBlank()) Text("Title is required")
                    },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                )

                Text("Surface type *", style = MaterialTheme.typography.labelLarge)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SurfaceType.values().forEach { st ->
                        FilterChip(
                            selected = st == surface,
                            onClick = { surface = st },
                            label = { Text(st.label(), maxLines = 1) },
                        )
                    }
                }

                Text("Trail characteristics", style = MaterialTheme.typography.titleMedium)
                Text(
                    "These appear on the hike card and help others know what to expect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RatingRow("Difficulty", difficulty, { difficulty = it })
                RatingRow("Mud risk", mud, { mud = it })
                RatingRow("Path clarity", clarity, { clarity = it })
                RatingRow("Fatigue", fatigue, { fatigue = it })
                RatingRow("Animal encounter risk", animal, { animal = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = water, onCheckedChange = { water = it })
                    Text(
                        "  Water sources available",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    Text(
                        if (isPrivate) "Private (only you)" else "Public",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

private fun SurfaceType.label() = when (this) {
    SurfaceType.MOUNTAIN -> "Mountain"
    SurfaceType.FOREST -> "Forest"
    SurfaceType.COASTAL -> "Coastal"
    SurfaceType.URBAN -> "Urban"
    SurfaceType.DESERT -> "Desert"
    SurfaceType.OTHER -> "Other"
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%d:%02d:%02d".format(h, m, s)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun MediaCaptureRow(
    isRecordingAudio: Boolean,
    onPhoto: (android.graphics.Bitmap) -> Unit,
    onStartAudio: () -> Unit,
    onStopAudio: () -> Unit,
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) onPhoto(bitmap)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                if (cameraPermission.status.isGranted) {
                    cameraLauncher.launch(null)
                } else {
                    cameraPermission.launchPermissionRequest()
                }
            },
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Photo")
        }

        Button(
            onClick = {
                if (isRecordingAudio) {
                    onStopAudio()
                } else if (micPermission.status.isGranted) {
                    onStartAudio()
                } else {
                    micPermission.launchPermissionRequest()
                }
            },
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecordingAudio) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isRecordingAudio) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isRecordingAudio) "Stop" else "Audio note")
        }
    }
}

@Composable
private fun CapturedMediaStrip(items: List<HikeMediaItem>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Captured (${items.size})",
            style = MaterialTheme.typography.labelLarge,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.size(width = 72.dp, height = 72.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    when (item.type) {
                        HikeMediaType.PHOTO -> AsyncImage(
                            model = File(item.filePath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        HikeMediaType.AUDIO -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Audio note",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
