package com.wildtrail.app.data.repository

import android.net.Uri
import android.util.Log
import com.wildtrail.app.data.local.dao.UserDao
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.StorageService
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.data.remote.dto.toDto
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.User
import com.wildtrail.app.util.LevelMath
import com.wildtrail.app.util.LocalImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class UserRepository(
    private val userDao: UserDao,
    private val firestore: FirestoreService,
    private val storage: StorageService,
    private val imageStore: LocalImageStore,
    private val hikeLogRepository: HikeLogRepository,
    private val externalScope: CoroutineScope,
) {

    fun observeUser(uid: String): Flow<User?> {
        firestore.observeUser(uid)
            .catch { Log.w(TAG, "Firestore user listener errored", it) }
            .onEach { dto ->
                if (dto != null) {
                    val remote = dto.toDomain()
                    val local = userDao.getById(remote.firebaseUid)?.toDomain()
                    userDao.upsert(remote.keepingLocalPicture(local).toEntity())
                }
            }
            .launchIn(externalScope)
        return userDao.observeById(uid).map { it?.toDomain() }
    }

    suspend fun getUser(uid: String): User? = userDao.getById(uid)?.toDomain()

    private fun observeCachedUser(uid: String): Flow<User?> =
        userDao.observeById(uid).map { it?.toDomain() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun withLiveCreatorPictures(hikes: Flow<List<HikeLog>>): Flow<List<HikeLog>> =
        hikes.flatMapLatest { list ->
            val ids = list.map { it.creatorFirebaseUid }.toSet()
            if (ids.isEmpty()) {
                flowOf(list)
            } else {
                combine(
                    ids.map { id -> observeCachedUser(id).map { id to it?.profilePictureUrl } },
                ) { pairs ->
                    val pics = pairs.toMap()
                    list.map { hike ->
                        val live = pics[hike.creatorFirebaseUid]
                        if (!live.isNullOrBlank()) {
                            hike.copy(creatorProfilePictureUrl = live)
                        } else {
                            hike
                        }
                    }
                }
            }
        }

    suspend fun updateUser(user: User) {
        userDao.upsert(user.toEntity())
        runCatching { firestore.upsertUser(user.toDto()) }
            .onFailure { Log.w(TAG, "Firestore profile update skipped", it) }
    }

    suspend fun incrementHikeStats(uid: String, distanceKm: Float, xpEarned: Int) {
        val current = userDao.getById(uid)?.toDomain() ?: return
        val newXp = current.xpPoints + xpEarned
        val updated = current.copy(
            xpPoints = newXp,
            level = LevelMath.levelForXp(newXp),
            totalHikesCount = current.totalHikesCount + 1,
            totalDistanceKm = current.totalDistanceKm + distanceKm,
            lastActive = System.currentTimeMillis(),
        )
        updateUser(updated)
    }

    suspend fun updateProfilePicture(uid: String, localUri: Uri): Boolean {
        val current = userDao.getById(uid)?.toDomain() ?: return false

        val localFile = runCatching { imageStore.saveProfilePicture(uid, localUri) }
            .onFailure { Log.w(TAG, "Local profile picture copy failed", it) }
            .getOrNull()
        if (localFile != null) {
            val localPath = Uri.fromFile(localFile).toString()
            userDao.upsert(current.copy(profilePictureUrl = localPath).toEntity())
            hikeLogRepository.syncCreatorInfo(
                uid, current.username, localPath, pushRemote = false,
            )
        }

        val uploadSource = localFile?.let(Uri::fromFile) ?: localUri
        return runCatching {
            val url = storage.uploadProfilePicture(uid, uploadSource)
            val latest = userDao.getById(uid)?.toDomain() ?: return@runCatching false
            updateUser(latest.copy(profilePictureUrl = url))
            hikeLogRepository.syncCreatorInfo(uid, latest.username, url, pushRemote = true)
            true
        }
            .onFailure { Log.w(TAG, "Profile picture upload skipped", it) }
            .getOrDefault(false)
    }

    suspend fun search(query: String): List<User> =
        userDao.searchByUsername(query).map { it.toDomain() }

    private companion object { const val TAG = "UserRepository" }
}
