package com.wildtrail.app.di

import android.content.Context
import android.hardware.SensorManager
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.wildtrail.app.BuildConfig
import com.wildtrail.app.data.local.WildTrailDatabase
import com.wildtrail.app.data.remote.FirebaseAuthService
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.StorageService
import com.wildtrail.app.data.remote.HikeApiService
import com.wildtrail.app.data.remote.WeatherApiService
import com.wildtrail.app.data.repository.AchievementRepository
import com.wildtrail.app.data.repository.AuthRepository
import com.wildtrail.app.data.repository.EmergencyContactRepository
import com.wildtrail.app.data.repository.HikeLogRepository
import com.wildtrail.app.data.repository.PredictRepository
import com.wildtrail.app.data.repository.ReviewSummaryRepository
import com.wildtrail.app.data.repository.SensorRepository
import com.wildtrail.app.data.repository.SocialRepository
import com.wildtrail.app.data.repository.UserRepository
import com.wildtrail.app.data.repository.WeatherRepository
import com.wildtrail.app.service.LocationServiceController
import com.wildtrail.app.util.BirdNetClassifier
import com.wildtrail.app.util.HikeMediaStore
import com.wildtrail.app.util.LocalImageStore
import com.wildtrail.app.util.LocationTracker
import com.wildtrail.app.util.PhotoDescriber
import com.wildtrail.app.util.ReviewImageStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface AppContainer {
    val authRepository: AuthRepository
    val userRepository: UserRepository
    val hikeLogRepository: HikeLogRepository
    val socialRepository: SocialRepository
    val achievementRepository: AchievementRepository
    val emergencyContactRepository: EmergencyContactRepository
    val weatherRepository: WeatherRepository
    val predictRepository: PredictRepository
    val reviewSummaryRepository: ReviewSummaryRepository
    val sensorRepository: SensorRepository
    val locationTracker: LocationTracker
    val locationServiceController: LocationServiceController
    val hikeMediaStore: HikeMediaStore
    val photoDescriber: PhotoDescriber
    val birdNetClassifier: BirdNetClassifier
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val appScope = CoroutineScope(
        SupervisorJob()
            + Dispatchers.Default
            + CoroutineExceptionHandler { _, t ->
                Log.e("WildTrailAppScope", "Uncaught coroutine error swallowed", t)
            },
    )

    private val database = WildTrailDatabase.getInstance(context)

    private val authService = FirebaseAuthService(FirebaseAuth.getInstance())
    private val firestore = FirestoreService(FirebaseFirestore.getInstance())
    private val storage = StorageService()
    private val localImageStore = LocalImageStore(context.applicationContext)
    private val reviewImageStore = ReviewImageStore(context.applicationContext)
    private val weatherHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .build()
    private val backendRetrofit = Retrofit.Builder()
        .baseUrl(normalizeBaseUrl(BuildConfig.WEATHER_BACKEND_BASE_URL))
        .client(weatherHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val weatherApiService: WeatherApiService =
        backendRetrofit.create(WeatherApiService::class.java)

    private val hikeApiService: HikeApiService =
        backendRetrofit.create(HikeApiService::class.java)

    override val authRepository: AuthRepository = AuthRepository(
        authService = authService,
        firestore = firestore,
        storage = storage,
        imageStore = localImageStore,
        userDao = database.userDao(),
        externalScope = appScope,
    )

    override val hikeLogRepository: HikeLogRepository = HikeLogRepository(
        hikeLogDao = database.hikeLogDao(),
        likeDao = database.likeDao(),
        reviewDao = database.trailReviewDao(),
        commentDao = database.hikeCommentDao(),
        userDao = database.userDao(),
        firestore = firestore,
        storage = storage,
        externalScope = appScope,
    )

    override val userRepository: UserRepository = UserRepository(
        userDao = database.userDao(),
        firestore = firestore,
        storage = storage,
        imageStore = localImageStore,
        hikeLogRepository = hikeLogRepository,
        externalScope = appScope,
    )

    override val socialRepository: SocialRepository = SocialRepository(
        reviewDao = database.trailReviewDao(),
        userFollowDao = database.userFollowDao(),
        followedTrailDao = database.followedTrailDao(),
        commentDao = database.hikeCommentDao(),
        firestore = firestore,
        storage = storage,
        reviewImageStore = reviewImageStore,
        externalScope = appScope,
    )

    override val achievementRepository: AchievementRepository = AchievementRepository(
        achievementDao = database.achievementDao(),
        firestore = firestore,
    )

    override val emergencyContactRepository: EmergencyContactRepository = EmergencyContactRepository(
        dao = database.emergencyContactDao(),
        firestore = firestore,
        externalScope = appScope,
    )

    override val weatherRepository: WeatherRepository = WeatherRepository(
        weatherApiService = weatherApiService,
        weatherDao = database.weatherDao(),
    )

    override val predictRepository: PredictRepository = PredictRepository(
        hikeApiService = hikeApiService,
    )

    override val reviewSummaryRepository: ReviewSummaryRepository = ReviewSummaryRepository(
        hikeApiService = hikeApiService,
    )

    override val sensorRepository: SensorRepository = SensorRepository(
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager,
    )

    override val locationTracker: LocationTracker = LocationTracker(context)

    override val locationServiceController: LocationServiceController =
        LocationServiceController(context.applicationContext)

    override val hikeMediaStore: HikeMediaStore = HikeMediaStore(context.applicationContext)

    override val photoDescriber: PhotoDescriber = PhotoDescriber()

    override val birdNetClassifier: BirdNetClassifier =
        BirdNetClassifier(context.applicationContext)

    private fun normalizeBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return "https://example.com/"
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
