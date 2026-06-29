package com.wildtrail.app.data.repository

import android.net.Uri
import android.util.Log
import com.wildtrail.app.data.local.dao.FollowedTrailDao
import com.wildtrail.app.data.local.dao.HikeCommentDao
import com.wildtrail.app.data.local.dao.TrailReviewDao
import com.wildtrail.app.data.local.dao.UserFollowDao
import com.wildtrail.app.data.local.entity.TrailReviewEntity
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.FirestoreService
import com.wildtrail.app.data.remote.StorageService
import com.wildtrail.app.data.remote.dto.TrailReviewDto
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.data.remote.dto.toDto
import com.wildtrail.app.domain.model.FollowedTrail
import com.wildtrail.app.domain.model.HikeComment
import com.wildtrail.app.domain.model.TrailReview
import com.wildtrail.app.domain.model.UserFollow
import com.wildtrail.app.util.ReviewImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class SocialRepository(
    private val reviewDao: TrailReviewDao,
    private val userFollowDao: UserFollowDao,
    private val followedTrailDao: FollowedTrailDao,
    private val commentDao: HikeCommentDao,
    private val firestore: FirestoreService,
    private val storage: StorageService,
    private val reviewImageStore: ReviewImageStore,
    private val externalScope: CoroutineScope,
) {

    fun observeReviewsForHike(hikeId: String): Flow<List<TrailReview>> {
        firestore.observeReviewsForHike(hikeId)
            .catch { Log.w(TAG, "Firestore reviews listener errored", it) }
            .onEach { dtos -> dtos.forEach { reviewDao.upsert(mergeKeepingLocalImages(it)) } }
            .launchIn(externalScope)
        return reviewDao.observeForHike(hikeId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun submitReview(review: TrailReview, localImageUris: List<Uri> = emptyList()) {
        val localFiles = if (localImageUris.isEmpty()) {
            emptyList()
        } else {
            runCatching { reviewImageStore.saveReviewImages(review.reviewId, localImageUris) }
                .onFailure { Log.w(TAG, "Local review image copy failed", it) }
                .getOrDefault(emptyList())
        }
        val localPaths = localFiles.map { Uri.fromFile(it).toString() }
        reviewDao.upsert(review.copy(imageUrls = localPaths).toEntity())

        runCatching { firestore.upsertReview(review.copy(imageUrls = emptyList()).toDto()) }
            .onFailure { Log.w(TAG, "Firestore review submit skipped", it) }

        if (localFiles.isEmpty()) return
        val httpsUrls = runCatching {
            localFiles.mapIndexed { index, file ->
                storage.uploadReviewImage(review.reviewId, index, Uri.fromFile(file))
            }
        }
            .onFailure { Log.w(TAG, "Review image upload skipped", it) }
            .getOrNull() ?: return

        val uploaded = review.copy(imageUrls = httpsUrls)
        reviewDao.upsert(uploaded.toEntity())
        runCatching { firestore.upsertReview(uploaded.toDto()) }
            .onFailure { Log.w(TAG, "Firestore review image sync skipped", it) }
    }

    suspend fun deleteReview(review: TrailReview) {
        reviewDao.deleteById(review.reviewId)
        runCatching { firestore.deleteReview(review.reviewId) }
            .onFailure { Log.w(TAG, "Firestore review delete skipped", it) }
        // Best-effort cleanup of uploaded review images.
        review.imageUrls
            .filter { it.startsWith("http") }
            .forEach { url ->
                runCatching { storage.deleteByUrl(url) }
                    .onFailure { Log.w(TAG, "Review image delete skipped", it) }
            }
    }

    suspend fun deleteComment(commentId: String) {
        commentDao.deleteById(commentId)
        runCatching { firestore.deleteComment(commentId) }
            .onFailure { Log.w(TAG, "Firestore comment delete skipped", it) }
    }

    private suspend fun mergeKeepingLocalImages(dto: TrailReviewDto): TrailReviewEntity {
        val remote = dto.toDomain()
        if (remote.imageUrls.isNotEmpty()) return remote.toEntity()
        val keptLocal = reviewDao.getById(dto.reviewId)?.imageUrls
            ?.filter { it.startsWith("file://") }
            .orEmpty()
        return if (keptLocal.isEmpty()) {
            remote.toEntity()
        } else {
            remote.copy(imageUrls = keptLocal).toEntity()
        }
    }

    fun observeAvgDifficulty(hikeId: String) = reviewDao.observeAvgDifficulty(hikeId)

    suspend fun follow(follow: UserFollow) {
        userFollowDao.upsert(follow.toEntity())
        runCatching { firestore.followUser(follow.toDto()) }
            .onFailure { Log.w(TAG, "Firestore follow skipped", it) }
    }

    suspend fun unfollow(followerUid: String, followeeUid: String) {
        userFollowDao.delete(followerUid, followeeUid)
        runCatching { firestore.unfollowUser(followerUid, followeeUid) }
            .onFailure { Log.w(TAG, "Firestore unfollow skipped", it) }
    }

    fun observeIsFollowing(follower: String, followee: String) =
        userFollowDao.observeIsFollowing(follower, followee)

    suspend fun followTrail(trail: FollowedTrail) {
        followedTrailDao.upsert(trail.toEntity())
        runCatching { firestore.followTrail(trail.toDto()) }
            .onFailure { Log.w(TAG, "Firestore follow-trail skipped", it) }
    }

    suspend fun unfollowTrail(uid: String, hikeId: String) {
        followedTrailDao.delete(uid, hikeId)
        runCatching { firestore.unfollowTrail(uid, hikeId) }
            .onFailure { Log.w(TAG, "Firestore unfollow-trail skipped", it) }
    }

    fun observeFollowedTrails(uid: String): Flow<List<FollowedTrail>> =
        followedTrailDao.observeForUser(uid).map { list -> list.map { it.toDomain() } }

    fun observeComments(hikeId: String): Flow<List<HikeComment>> {
        firestore.observeCommentsForHike(hikeId)
            .catch { Log.w(TAG, "Firestore comments listener errored", it) }
            .onEach { dtos -> commentDao.upsertAll(dtos.map { it.toDomain().toEntity() }) }
            .launchIn(externalScope)
        return commentDao.observeForHike(hikeId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun postComment(comment: HikeComment) {
        commentDao.upsert(comment.toEntity())
        runCatching { firestore.upsertComment(comment.toDto()) }
            .onFailure { Log.w(TAG, "Firestore comment post skipped", it) }
    }

    private companion object { const val TAG = "SocialRepository" }
}
