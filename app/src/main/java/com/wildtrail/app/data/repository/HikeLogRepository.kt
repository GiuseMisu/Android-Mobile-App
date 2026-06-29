package com.wildtrail.app.data.repository

import android.net.Uri
import android.util.Log
import com.wildtrail.app.data.local.dao.HikeCommentDao
import com.wildtrail.app.data.local.dao.HikeLogDao
import com.wildtrail.app.data.local.dao.LikeDao
import com.wildtrail.app.data.local.dao.TrailReviewDao
import com.wildtrail.app.data.local.dao.UserDao
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.StorageService
import com.wildtrail.app.data.remote.dto.HikeLogDto
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.data.remote.dto.toDto
import com.wildtrail.app.domain.model.HikeFilter
import com.wildtrail.app.domain.model.HikeLog
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType
import com.wildtrail.app.domain.model.Like
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class HikeLogRepository(
    private val hikeLogDao: HikeLogDao,
    private val likeDao: LikeDao,
    private val reviewDao: TrailReviewDao,
    private val commentDao: HikeCommentDao,
    private val userDao: UserDao,
    private val firestore: FirestoreService,
    private val storage: StorageService,
    private val externalScope: CoroutineScope,
) {

    private val backfilledUids = ConcurrentHashMap.newKeySet<String>()

    fun observePublicFeed(limit: Int = 50): Flow<List<HikeLog>> {
        firestore.observePublicHikes(limit.toLong())
            .catch { Log.w(TAG, "Firestore public feed listener errored", it) }
            .onEach { dtos ->
                hikeLogDao.upsertAll(dtos.map { mergeLocalMedia(it) })
                backfillCreators(dtos)
            }
            .launchIn(externalScope)
        return hikeLogDao.observePublicFeed(limit).map { list -> list.map { it.toDomain() } }
    }

    fun observeMyHikes(uid: String): Flow<List<HikeLog>> {
        firestore.observeHikesByCreator(uid)
            .catch { Log.w(TAG, "Firestore my-hikes listener errored", it) }
            .onEach { dtos ->
                hikeLogDao.upsertAll(dtos.map { mergeLocalMedia(it) })
                backfillCreators(dtos)
            }
            .launchIn(externalScope)
        return hikeLogDao.observeByCreator(uid).map { list -> list.map { it.toDomain() } }
    }

    // The creator keeps on-device file paths (instant playback); everyone else uses the
    // remote URLs that travelled in the Firestore doc. So: prefer a non-empty local copy,
    // otherwise fall back to the media items the DTO carried.
    private suspend fun mergeLocalMedia(dto: HikeLogDto): com.wildtrail.app.data.local.entity.HikeLogEntity {
        val domain = dto.toDomain()
        val localMedia = hikeLogDao.getById(dto.hikeId)?.mediaItems
        return domain
            .copy(mediaItems = localMedia?.takeIf { it.isNotEmpty() } ?: domain.mediaItems)
            .toEntity()
    }

    fun observeHike(id: String): Flow<HikeLog?> =
        hikeLogDao.observeById(id).map { it?.toDomain() }

    fun observeLikedHikes(uid: String): Flow<List<HikeLog>> =
        hikeLogDao.observeLikedHikes(uid).map { list -> list.map { it.toDomain() } }

    suspend fun saveHike(hike: HikeLog) {
        // Upload captured media to Storage first, then persist the SAME remote URLs both
        // locally and to Firestore. Storing URLs locally (rather than on-device paths)
        // keeps the document consistent: later metadata re-pushes (likes, rating, creator
        // info) won't overwrite the remote media URLs with unreachable local paths.
        val stored = hike.copy(mediaItems = uploadMedia(hike))
        hikeLogDao.upsert(stored.toEntity())
        runCatching { firestore.upsertHike(stored.toDto()) }
            .onFailure { Log.w(TAG, "Firestore hike save skipped", it) }
    }

    private suspend fun uploadMedia(hike: HikeLog): List<HikeMediaItem> {
        if (hike.mediaItems.isEmpty()) return hike.mediaItems
        return hike.mediaItems.map { item ->
            if (item.filePath.startsWith("http")) return@map item // already uploaded
            runCatching {
                val ext = if (item.type == HikeMediaType.AUDIO) "m4a" else "jpg"
                val url = storage.uploadHikeMedia(
                    hikeId = hike.hikeId,
                    mediaId = item.id,
                    ext = ext,
                    localUri = Uri.fromFile(File(item.filePath)),
                )
                item.copy(filePath = url)
            }.getOrElse {
                Log.w(TAG, "Hike media upload skipped for ${item.id}", it)
                item // upload failed (e.g. offline); keep local path as a best-effort fallback
            }
        }
    }

    suspend fun deleteHike(hike: HikeLog) {
        val hikeId = hike.hikeId

        // Cascade: delete the hike's reviews, comments and likes FIRST — while the hike doc
        // still exists, so the owner-of-hike clause in the security rules still resolves.

        // Reviews (+ their uploaded images).
        val reviewImageUrls = reviewDao.getForHike(hikeId).flatMap { it.imageUrls }
        runCatching { firestore.deleteReviewsForHike(hikeId) }
            .onFailure { Log.w(TAG, "Firestore reviews cascade-delete skipped", it) }
        reviewDao.deleteForHike(hikeId)
        reviewImageUrls.filter { it.startsWith("http") }.forEach { url ->
            runCatching { storage.deleteByUrl(url) }
                .onFailure { Log.w(TAG, "Review image delete skipped", it) }
        }

        // Comments.
        runCatching { firestore.deleteCommentsForHike(hikeId) }
            .onFailure { Log.w(TAG, "Firestore comments cascade-delete skipped", it) }
        commentDao.deleteForHike(hikeId)

        // Likes.
        runCatching { firestore.deleteLikesForHike(hikeId) }
            .onFailure { Log.w(TAG, "Firestore likes cascade-delete skipped", it) }
        likeDao.deleteForHike(hikeId)

        // The hike's own captured media.
        hike.mediaItems
            .map { it.filePath }
            .filter { it.startsWith("http") }
            .forEach { url ->
                runCatching { storage.deleteByUrl(url) }
                    .onFailure { Log.w(TAG, "Hike media delete skipped", it) }
            }

        // Finally the hike document itself.
        runCatching { firestore.deleteHike(hikeId) }
            .onFailure { Log.w(TAG, "Firestore hike delete skipped", it) }
        hikeLogDao.deleteById(hikeId)
    }

    suspend fun syncCreatorInfo(
        uid: String,
        username: String,
        profilePictureUrl: String?,
        pushRemote: Boolean = true,
    ) {
        hikeLogDao.getByCreator(uid).forEach { row ->
            if (row.creatorUsername != username ||
                row.creatorProfilePictureUrl != profilePictureUrl
            ) {
                val patched = row.copy(
                    creatorUsername = username,
                    creatorProfilePictureUrl = profilePictureUrl,
                )
                hikeLogDao.upsert(patched)
                if (pushRemote) {
                    runCatching { firestore.upsertHike(patched.toDomain().toDto()) }
                        .onFailure { Log.w(TAG, "Firestore creator-info sync skipped", it) }
                }
            }
        }
    }

    suspend fun search(query: String): List<HikeLog> =
        hikeLogDao.search(query).map { it.toDomain() }

    // returns a Flow (not a one-shot list) so a likesCount change re-emits the results
    fun filter(query: String, criteria: HikeFilter): Flow<List<HikeLog>> {
        val difficulties = criteria.difficulties.toList().ifEmpty { listOf(0) }
        val surfaces = criteria.surfaceTypes.map { it.name }.ifEmpty { listOf("") }
        return hikeLogDao.filter(
            q = query.trim(),
            minKm = criteria.minKm,
            maxKm = criteria.maxKm,
            minElevation = criteria.minElevation,
            maxElevation = criteria.maxElevation,
            anyDifficulty = criteria.difficulties.isEmpty(),
            difficulties = difficulties,
            anySurface = criteria.surfaceTypes.isEmpty(),
            surfaces = surfaces,
        ).map { list -> list.map { it.toDomain() } }
    }

    suspend fun refresh() {
        kotlinx.coroutines.delay(600L)
    }

    fun observeLikeCount(hikeId: String): Flow<Int> {
        firestore.observeLikesForHike(hikeId)
            .catch { Log.w(TAG, "Firestore likes listener errored", it) }
            .onEach { dtos ->
                dtos.forEach { likeDao.insert(it.toDomain().toEntity()) }
            }
            .launchIn(externalScope)
        return likeDao.observeLikeCount(hikeId)
    }

    fun observeIsLiked(uid: String, hikeId: String): Flow<Boolean> =
        likeDao.observeIsLikedByUser(uid, hikeId)

    fun observeMyLikedHikeIds(uid: String): Flow<Set<String>> =
        likeDao.observeMyLikedHikeIds(uid).map { it.toSet() }

    suspend fun setLiked(uid: String, hikeId: String, liked: Boolean) {
        if (liked) {
            val like = Like(uid, hikeId, System.currentTimeMillis())
            likeDao.insert(like.toEntity())
            runCatching { firestore.like(like.toDto()) }
                .onFailure { Log.w(TAG, "Firestore like skipped", it) }
        } else {
            likeDao.delete(uid, hikeId)
            runCatching { firestore.unlike(uid, hikeId) }
                .onFailure { Log.w(TAG, "Firestore unlike skipped", it) }
        }
        val freshCount = likeDao.countLikes(hikeId)
        val hike = hikeLogDao.getById(hikeId)?.toDomain()
        if (hike != null && hike.likesCount != freshCount) {
            val updated = hike.copy(likesCount = freshCount)
            hikeLogDao.upsert(updated.toEntity())
            runCatching { firestore.upsertHike(updated.toDto()) }
                .onFailure { Log.w(TAG, "Firestore hike likesCount sync skipped", it) }
        }
    }

    suspend fun refreshAggregateRating(hikeId: String) {
        val avg = reviewDao.getAvgOverallRating(hikeId) ?: 0.0
        val count = reviewDao.getCount(hikeId)
        val current = hikeLogDao.getById(hikeId) ?: return
        val updated = current.copy(
            averageRating = avg.toFloat(),
            reviewCount = count,
        )
        hikeLogDao.upsert(updated)
        runCatching { firestore.upsertHike(updated.toDomain().toDto()) }
            .onFailure { Log.w(TAG, "Firestore aggregate rating sync skipped", it) }
    }

    private fun backfillCreators(dtos: List<HikeLogDto>) {
        val creatorUids = dtos.map { it.creatorFirebaseUid }.toSet()
        if (creatorUids.isEmpty()) return
        externalScope.launch {
            creatorUids.forEach { uid ->
                if (!backfilledUids.add(uid)) return@forEach
                runCatching {
                    val user = firestore.getUser(uid)?.toDomain() ?: return@runCatching
                    if (user.username.isBlank()) return@runCatching
                    val local = userDao.getById(uid)?.toDomain()
                    userDao.upsert(user.keepingLocalPicture(local).toEntity())
                    hikeLogDao.getByCreator(uid).forEach { row ->
                        val newPic = user.profilePictureUrl ?: row.creatorProfilePictureUrl
                        if (row.creatorUsername == user.username &&
                            row.creatorProfilePictureUrl == newPic
                        ) {
                            return@forEach
                        }
                        val patched = row.copy(
                            creatorUsername = user.username,
                            creatorProfilePictureUrl = newPic,
                        )
                        hikeLogDao.upsert(patched)
                        if (newPic?.startsWith("file://") != true) {
                            runCatching { firestore.upsertHike(patched.toDomain().toDto()) }
                                .onFailure { Log.w(TAG, "Firestore creator backfill push skipped", it) }
                        }
                    }
                }.onFailure { Log.w(TAG, "Creator backfill skipped for $uid", it) }
            }
        }
    }

    private companion object { const val TAG = "HikeLogRepository" }
}
