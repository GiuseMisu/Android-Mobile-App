package com.wildtrail.app.ui.tracking

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.BuildConfig
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.data.repository.EmergencyContactRepository
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.UserRepository
import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType
import com.wildtrail.app.domain.model.SurfaceType
import com.wildtrail.app.domain.usecase.DetectFallUseCase
import com.wildtrail.app.domain.usecase.FallDetectionEvent
import com.wildtrail.app.service.LocationServiceController
import com.wildtrail.app.util.AudioRecorder
import com.wildtrail.app.util.HikeMath
import com.wildtrail.app.util.HikeMediaStore
import com.wildtrail.app.util.LocationTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class TrackingUiState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val routePoints: List<GeoPoint> = emptyList(),
    val distanceKm: Float = 0f,
    val elevationGainM: Int = 0,
    val durationSec: Long = 0L,
    val avgSpeedKmh: Float = 0f,
    val hasPermission: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val currentLocation: GeoPoint? = null,
    val mediaItems: List<HikeMediaItem> = emptyList(),
    val isRecordingAudio: Boolean = false,
    val emergency: EmergencyUiState? = null,
)

data class EmergencyUiState(
    val contactName: String,
    val countdownSeconds: Int = EMERGENCY_COUNTDOWN_SECONDS,
)

enum class TrackingStatus { IDLE, RECORDING, PAUSED, STOPPED }

const val EMERGENCY_COUNTDOWN_SECONDS = 15

class TrackingViewModel(
    private val locationTracker: LocationTracker,
    private val locationServiceController: LocationServiceController,
    private val hikeLogRepository: HikeLogRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val mediaStore: HikeMediaStore,
    private val audioRecorder: AudioRecorder,
    private val detectFallUseCase: DetectFallUseCase,
    private val emergencyContactRepository: EmergencyContactRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackingUiState())
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    private val _events = Channel<TrackingEvent>(Channel.BUFFERED)
    val events: Flow<TrackingEvent> = _events.receiveAsFlow()

    private var locationJob: Job? = null

    private var fallDetectionJob: Job? = null

    private var previewJob: Job? = null
    private var startTimeMs: Long = 0L
    private var elapsedBeforePauseMs: Long = 0L

    init {
        val granted = locationTracker.hasLocationPermission()
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) startLocationPreview()
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) startLocationPreview()
    }

    private fun startLocationPreview() {
        if (previewJob?.isActive == true) return
        previewJob = viewModelScope.launch {
            try {
                locationTracker.observeLocation(intervalMs = 3_000L).collect { point ->
                    _uiState.update { st ->
                        if (st.status == TrackingStatus.IDLE ||
                            st.status == TrackingStatus.STOPPED
                        ) {
                            st.copy(currentLocation = point)
                        } else {
                            st
                        }
                    }
                }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(errorMessage = e.message, hasPermission = false) }
            }
        }
    }

    fun start() {
        if (!locationTracker.hasLocationPermission()) {
            _uiState.update { it.copy(errorMessage = "Location permission required") }
            return
        }
        startTimeMs = System.currentTimeMillis()
        elapsedBeforePauseMs = 0L
        previewJob?.cancel()
        _uiState.update { it.copy(status = TrackingStatus.RECORDING, routePoints = emptyList(), errorMessage = null) }
        locationServiceController.start()
        beginCollecting()
        beginTimer()
        startFallDetection()
    }

    fun pause() {
        if (_uiState.value.status != TrackingStatus.RECORDING) return
        elapsedBeforePauseMs += System.currentTimeMillis() - startTimeMs
        locationJob?.cancel()
        locationServiceController.stop()
        stopFallDetection()
        _uiState.update { it.copy(status = TrackingStatus.PAUSED) }
    }

    fun resume() {
        if (_uiState.value.status != TrackingStatus.PAUSED) return
        if (_uiState.value.emergency != null) return
        startTimeMs = System.currentTimeMillis()
        _uiState.update { it.copy(status = TrackingStatus.RECORDING) }
        locationServiceController.start()
        beginCollecting()
        beginTimer()
        startFallDetection()
    }

    fun stop() {
        locationJob?.cancel()
        locationServiceController.stop()
        stopFallDetection()
        if (_uiState.value.status == TrackingStatus.RECORDING) {
            elapsedBeforePauseMs += System.currentTimeMillis() - startTimeMs
        }
        _uiState.update { it.copy(status = TrackingStatus.STOPPED) }
    }

    private fun startFallDetection() {
        if (fallDetectionJob?.isActive == true) return
        Log.d(TAG, "Fall detection ACTIVE (recording started)")
        fallDetectionJob = viewModelScope.launch {
            detectFallUseCase().collect { event ->
                when (event) {
                    is FallDetectionEvent.FallDetected -> onFallDetected()
                }
            }
        }
    }

    private fun stopFallDetection() {
        if (fallDetectionJob != null) Log.d(TAG, "Fall detection stopped")
        fallDetectionJob?.cancel()
        fallDetectionJob = null
    }

    private fun onFallDetected() {
        if (_uiState.value.status != TrackingStatus.RECORDING) return
        Log.w(TAG, "onFallDetected(): fall confirmed by sensors")
        pause()
        raiseEmergency()
    }

    fun simulateFall() {
        Log.w(TAG, "simulateFall(): manually raising emergency overlay (debug)")
        if (_uiState.value.status == TrackingStatus.RECORDING) pause()
        raiseEmergency()
    }

    private fun raiseEmergency() {
        if (_uiState.value.emergency != null) return
        viewModelScope.launch {
            val contactName = resolveEmergencyContactName()
            _uiState.update { it.copy(emergency = EmergencyUiState(contactName = contactName)) }
            _events.send(TrackingEvent.ShowEmergencyOverlay)
        }
    }

    fun onEmergencyCancelled() {
        _uiState.update { it.copy(emergency = null) }
    }

    fun onEmergencyCountdownFinished() {
        val contactName = _uiState.value.emergency?.contactName ?: return
        initiateEmergencyCall(contactName)
        _uiState.update { it.copy(emergency = null) }
    }

    private fun initiateEmergencyCall(contactName: String) {
        Log.w(TAG, "initiateEmergencyCall(): emergency call would be placed to '$contactName'")
        viewModelScope.launch { _events.send(TrackingEvent.EmergencyCallPlaced(contactName)) }
    }

    private suspend fun resolveEmergencyContactName(): String {
        val uid = (authRepository.authState.value as? AuthState.SignedIn)?.user?.firebaseUid
            ?: return DEFAULT_CONTACT_LABEL
        val contacts = runCatching { emergencyContactRepository.getFallNotifyList(uid) }
            .getOrDefault(emptyList())
        val chosen = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
        return chosen?.name ?: DEFAULT_CONTACT_LABEL
    }

    fun saveHike(
        title: String,
        description: String?,
        surfaceType: SurfaceType,
        isPrivate: Boolean,
        difficultyLevel: Int,
        mudRisk: Int,
        pathClarity: Int,
        fatigueLevel: Int,
        animalEncounterRisk: Int,
        waterAvailability: Boolean,
    ) {
        val state = _uiState.value
        val auth = authRepository.authState.value
        if (auth !is AuthState.SignedIn) {
            _uiState.update { it.copy(errorMessage = "You must be signed in to save a hike") }
            return
        }
        val now = System.currentTimeMillis()
        val durationSec = state.durationSec
        viewModelScope.launch {
            val creator = runCatching { userRepository.getUser(auth.user.firebaseUid) }
                .getOrNull() ?: auth.user
            val hike = HikeLog(
            hikeId = UUID.randomUUID().toString(),
            creatorFirebaseUid = creator.firebaseUid,
            creatorUsername = creator.username,
            creatorProfilePictureUrl = creator.profilePictureUrl,
            workoutId = null,
            title = title,
            description = description,
            avgSpeedKmh = state.avgSpeedKmh,
            stepCount = 0,
            caloriesBurned = HikeMath.estimateCalories(state.distanceKm, state.elevationGainM),
            coverPhotoUrl = null,
            xpEarned = HikeMath.xpFromHike(state.distanceKm, state.elevationGainM),
            likesCount = 0,
            surfaceType = surfaceType,
            lengthKm = state.distanceKm,
            durationSeconds = durationSec,
            startedAt = now - durationSec * 1000L,
            endedAt = now,
            elevationGainMeters = state.elevationGainM,
            routeCoordinates = state.routePoints,
            isPrivate = isPrivate,
            difficultyLevel = difficultyLevel,
            mudRisk = mudRisk,
            pathClarity = pathClarity,
            fatigueLevel = fatigueLevel,
            animalEncounterRisk = animalEncounterRisk,
            waterAvailability = waterAvailability,
            averageRating = 0f,
            reviewCount = 0,
            mediaItems = state.mediaItems,
            )
            runCatching {
                hikeLogRepository.saveHike(hike)
                runCatching {
                    userRepository.incrementHikeStats(
                        uid = creator.firebaseUid,
                        distanceKm = hike.lengthKm,
                        xpEarned = hike.xpEarned,
                    )
                }
            }
                .onSuccess { _uiState.update { it.copy(saved = true) } }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Could not save hike") }
                }
        }
    }

    fun resetAfterSave() {
        if (_uiState.value.isRecordingAudio) runCatching { audioRecorder.stop() }
        locationServiceController.stop()
        stopFallDetection()
        val granted = locationTracker.hasLocationPermission()
        _uiState.value = TrackingUiState(hasPermission = granted)
        if (granted) startLocationPreview()
    }

    fun capturePhoto(bitmap: Bitmap) {
        val location = _uiState.value.currentLocation ?: run {
            _uiState.update { it.copy(errorMessage = "No GPS fix yet — try again in a moment") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val file = mediaStore.savePhoto(bitmap)
                addMediaItem(HikeMediaType.PHOTO, file.absolutePath, location)
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Could not save photo") }
            }
        }
    }

    fun startAudioRecording() {
        if (_uiState.value.isRecordingAudio) return
        val target = mediaStore.newAudioFile()
        runCatching { audioRecorder.start(target) }
            .onSuccess { _uiState.update { it.copy(isRecordingAudio = true) } }
            .onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "Could not start recording") }
            }
    }

    fun stopAudioRecording() {
        if (!_uiState.value.isRecordingAudio) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { audioRecorder.stop() }
            _uiState.update { it.copy(isRecordingAudio = false) }
            val location = _uiState.value.currentLocation
            if (file != null && location != null) {
                addMediaItem(HikeMediaType.AUDIO, file.absolutePath, location)
            }
        }
    }

    private fun addMediaItem(type: HikeMediaType, path: String, location: GeoPoint) {
        val item = HikeMediaItem(
            id = UUID.randomUUID().toString(),
            type = type,
            filePath = path,
            lat = location.lat,
            lng = location.lng,
            timestamp = System.currentTimeMillis(),
        )
        _uiState.update { it.copy(mediaItems = it.mediaItems + item) }
    }

    private fun beginCollecting() {
        locationJob = viewModelScope.launch {
            try {
                // Fixes fed by the foreground LocationService, so they keep arriving
                // with the screen off instead of pausing until the screen wakes.
                locationTracker.liveLocations.collect { point ->
                    _uiState.update { state ->
                        val newRoute = state.routePoints + point
                        val distanceKm = HikeMath.totalDistanceKm(newRoute)
                        val gain = HikeMath.elevationGainMeters(newRoute)
                        state.copy(
                            routePoints = newRoute,
                            currentLocation = point,
                            distanceKm = distanceKm,
                            elevationGainM = gain,
                            avgSpeedKmh = HikeMath.avgSpeedKmh(distanceKm, state.durationSec),
                        )
                    }
                }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(errorMessage = e.message, hasPermission = false) }
            }
        }
    }

    private fun beginTimer() {
        viewModelScope.launch {
            while (isActive && _uiState.value.status == TrackingStatus.RECORDING) {
                kotlinx.coroutines.delay(1_000L)
                _uiState.update { state ->
                    val totalMs = elapsedBeforePauseMs + (System.currentTimeMillis() - startTimeMs)
                    val durSec = totalMs / 1_000L
                    state.copy(
                        durationSec = durSec,
                        avgSpeedKmh = HikeMath.avgSpeedKmh(state.distanceKm, durSec),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "TrackingViewModel"

        private const val DEFAULT_CONTACT_LABEL = "Emergency Services"

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                TrackingViewModel(
                    locationTracker = app.container.locationTracker,
                    locationServiceController = app.container.locationServiceController,
                    hikeLogRepository = app.container.hikeLogRepository,
                    userRepository = app.container.userRepository,
                    authRepository = app.container.authRepository,
                    mediaStore = app.container.hikeMediaStore,
                    audioRecorder = AudioRecorder(app.applicationContext),
                    detectFallUseCase = DetectFallUseCase(
                        sensorRepository = app.container.sensorRepository,
                        debugLog = if (BuildConfig.DEBUG) {
                            { msg -> Log.d("FallDetection", msg) }
                        } else {
                            null
                        },
                    ),
                    emergencyContactRepository = app.container.emergencyContactRepository,
                )
            }
        }
    }
}
