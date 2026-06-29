package com.wildtrail.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wildtrail.app.data.local.entity.HikeLogEntity
import com.wildtrail.app.data.local.entity.UserEntity
import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.domain.model.SurfaceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WildTrailDatabaseTest {

    private lateinit var db: WildTrailDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WildTrailDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun userDao_upsertAndRead() = runBlocking {
        val dao = db.userDao()
        val u = UserEntity(
            firebaseUid = "uid-1",
            username = "giovanni",
            sex = Sex.MALE,
            dateOfBirth = 0L,
            country = "IT",
            level = 2,
            xpPoints = 120,
            totalDistanceKm = 14.2f,
            totalHikesCount = 3,
            profilePictureUrl = null,
            bio = null,
            emergencyContactNumber = "9999999",
            createdAt = 1L,
            lastActive = 2L,
            isPublic = true,
        )
        dao.upsert(u)

        val read = dao.getById("uid-1")
        assertEquals(u, read)
    }

    @Test
    fun hikeLogDao_persistsRoute() = runBlocking {
        val userDao = db.userDao()
        val hikeDao = db.hikeLogDao()

        userDao.upsert(seedUser())

        val route = listOf(
            GeoPoint(45.0, 9.0, altitudeM = 100.0, timestamp = 1L),
            GeoPoint(45.001, 9.001, altitudeM = 110.0, timestamp = 2L),
        )
        val hike = sampleHike(route = route)

        hikeDao.upsert(hike)
        val read = hikeDao.getById("h1")
        assertNotNull(read)
        assertEquals(route, read!!.routeCoordinates)
    }

    @Test
    fun deletingUser_cascadesToHikes() = runBlocking {
        val userDao = db.userDao()
        val hikeDao = db.hikeLogDao()

        userDao.upsert(seedUser())
        hikeDao.upsert(sampleHike())
        userDao.deleteById("uid-1")

        assertNull(hikeDao.getById("h1"))
    }

    @Test
    fun publicFeed_observesNewHikes() = runBlocking {
        val userDao = db.userDao()
        val hikeDao = db.hikeLogDao()

        userDao.upsert(seedUser())
        hikeDao.upsert(sampleHike())

        val feed = hikeDao.observePublicFeed(50).first()
        assertEquals(1, feed.size)
        assertEquals("h1", feed.first().hikeId)
    }

    private fun seedUser() = UserEntity(
        firebaseUid = "uid-1", username = "g", sex = null, dateOfBirth = null,
        country = null, level = 1, xpPoints = 0, totalDistanceKm = 0f,
        totalHikesCount = 0, profilePictureUrl = null, bio = null,
        emergencyContactNumber = "9999999",
        createdAt = 0L, lastActive = 0L, isPublic = true,
    )

    private fun sampleHike(
        id: String = "h1",
        route: List<GeoPoint> = emptyList(),
    ) = HikeLogEntity(
        hikeId = id, creatorFirebaseUid = "uid-1",
        creatorUsername = "g", creatorProfilePictureUrl = null, workoutId = null,
        title = "x", description = null, avgSpeedKmh = 0f, stepCount = 0,
        caloriesBurned = 0, coverPhotoUrl = null, xpEarned = 0, likesCount = 0,
        surfaceType = SurfaceType.OTHER, lengthKm = 0f, durationSeconds = 0,
        startedAt = 0, endedAt = 0, elevationGainMeters = 0,
        routeCoordinates = route, isPrivate = false,
        difficultyLevel = 3, mudRisk = 3, pathClarity = 3, fatigueLevel = 3,
        animalEncounterRisk = 3, waterAvailability = false,
        averageRating = 0f, reviewCount = 0,
    )
}
