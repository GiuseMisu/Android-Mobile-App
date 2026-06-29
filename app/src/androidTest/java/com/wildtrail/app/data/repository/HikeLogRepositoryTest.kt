package com.wildtrail.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wildtrail.app.data.local.WildTrailDatabase
import com.wildtrail.app.data.local.entity.UserEntity
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.StorageService
import com.wildtrail.app.data.remote.dto.HikeLogDto
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.SurfaceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HikeLogRepositoryTest {

    private lateinit var db: WildTrailDatabase
    private lateinit var fakeFirestore: FakeFirestoreService
    private lateinit var repo: HikeLogRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, WildTrailDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.userDao().upsert(
            UserEntity(
                firebaseUid = "uid-1", username = "g", sex = null, dateOfBirth = null,
                country = null, level = 1, xpPoints = 0, totalDistanceKm = 0f,
                totalHikesCount = 0, profilePictureUrl = null, bio = null,
                emergencyContactNumber = "9999999",
                createdAt = 0L, lastActive = 0L, isPublic = true,
            ),
        )
        fakeFirestore = FakeFirestoreService()
        repo = HikeLogRepository(
            hikeLogDao = db.hikeLogDao(),
            likeDao = db.likeDao(),
            reviewDao = db.trailReviewDao(),
            commentDao = db.hikeCommentDao(),
            userDao = db.userDao(),
            firestore = fakeFirestore,
            storage = StorageService(),
            externalScope = scope,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun saveHike_appearsInLocalFeed() = runBlocking {
        val hike = sampleHike("h1")
        repo.saveHike(hike)
        val feed = repo.observeMyHikes("uid-1").first()
        assertEquals(1, feed.size)
        assertEquals("h1", feed.first().hikeId)
    }

    @Test
    fun saveHike_pushesToFirestore() = runBlocking {
        val hike = sampleHike("h1")
        repo.saveHike(hike)
        assertEquals(1, fakeFirestore.upsertHikeCalls.size)
        assertEquals("h1", fakeFirestore.upsertHikeCalls.first().hikeId)
    }

    private fun sampleHike(id: String) = HikeLog(
        hikeId = id,
        creatorFirebaseUid = "uid-1",
        creatorUsername = "g",
        creatorProfilePictureUrl = null,
        workoutId = null,
        title = "Sample",
        description = null,
        avgSpeedKmh = 4f,
        stepCount = 0,
        caloriesBurned = 100,
        coverPhotoUrl = null,
        xpEarned = 10,
        likesCount = 0,
        surfaceType = SurfaceType.MOUNTAIN,
        lengthKm = 1f,
        durationSeconds = 600L,
        startedAt = 0L,
        endedAt = 0L,
        elevationGainMeters = 50,
        routeCoordinates = emptyList(),
        isPrivate = false,
        difficultyLevel = 3,
        mudRisk = 3,
        pathClarity = 3,
        fatigueLevel = 3,
        animalEncounterRisk = 3,
        waterAvailability = false,
        averageRating = 0f,
        reviewCount = 0,
    )
}

class FakeFirestoreService : FirestoreService(db = null) {
    val upsertHikeCalls = mutableListOf<HikeLogDto>()
    private val publicHikes = MutableStateFlow<List<HikeLogDto>>(emptyList())

    override suspend fun upsertHike(dto: HikeLogDto) {
        upsertHikeCalls.add(dto)
    }

    override fun observePublicHikes(limit: Long): Flow<List<HikeLogDto>> = publicHikes

    override fun observeHikesByCreator(uid: String): Flow<List<HikeLogDto>> = publicHikes
}
