package com.wildtrail.app.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import com.wildtrail.app.data.remote.dto.AchievementDefinitionDto
import com.wildtrail.app.data.remote.dto.EmergencyContactDto
import com.wildtrail.app.data.remote.dto.FollowedTrailDto
import com.wildtrail.app.data.remote.dto.HikeCommentDto
import com.wildtrail.app.data.remote.dto.HikeLogDto
import com.wildtrail.app.data.remote.dto.LikeDto
import com.wildtrail.app.data.remote.dto.TrailReviewDto
import com.wildtrail.app.data.remote.dto.UserAchievementDto
import com.wildtrail.app.data.remote.dto.UserDto
import com.wildtrail.app.data.remote.dto.UserFollowDto
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

open class FirestoreService(
    private val db: FirebaseFirestore? = null,
) {

    private val realDb: FirebaseFirestore
        get() = db ?: FirebaseFirestore.getInstance()

    private val users get() = realDb.collection(USERS)
    private val hikes get() = realDb.collection(HIKES)
    private val reviews get() = realDb.collection(REVIEWS)
    private val follows get() = realDb.collection(FOLLOWS)
    private val followedTrails get() = realDb.collection(FOLLOWED_TRAILS)
    private val comments get() = realDb.collection(COMMENTS)
    private val achievementDefs get() = realDb.collection(ACHIEVEMENT_DEFS)
    private val userAchievements get() = realDb.collection(USER_ACHIEVEMENTS)
    private val emergencyContacts get() = realDb.collection(EMERGENCY_CONTACTS)
    private val likes get() = realDb.collection(LIKES)

    open suspend fun upsertUser(dto: UserDto) {
        users.document(dto.firebaseUid).set(dto).await()
    }

    open suspend fun getUser(uid: String): UserDto? =
        users.document(uid).get().await().toObject<UserDto>()

    open fun observeUser(uid: String): Flow<UserDto?> = callbackFlow {
        val reg = users.document(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                close(err); return@addSnapshotListener
            }
            trySend(snap?.toObject<UserDto>())
        }
        awaitClose { reg.remove() }
    }

    open suspend fun upsertHike(dto: HikeLogDto) {
        hikes.document(dto.hikeId).set(dto).await()
    }

    open suspend fun deleteHike(hikeId: String) {
        hikes.document(hikeId).delete().await()
    }

    // Cascade helpers: delete every doc in a collection whose `hikeId` matches.
    // Chunked into batches to stay under Firestore's 500-writes-per-batch limit.
    private suspend fun deleteByHikeId(collection: CollectionReference, hikeId: String) {
        val snap = collection.whereEqualTo("hikeId", hikeId).get().await()
        snap.documents.chunked(400).forEach { chunk ->
            val batch = realDb.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    open suspend fun deleteReviewsForHike(hikeId: String) = deleteByHikeId(reviews, hikeId)
    open suspend fun deleteCommentsForHike(hikeId: String) = deleteByHikeId(comments, hikeId)
    open suspend fun deleteLikesForHike(hikeId: String) = deleteByHikeId(likes, hikeId)

    open fun observePublicHikes(limit: Long = 50): Flow<List<HikeLogDto>> = callbackFlow {
        val reg = hikes
            .orderBy("endedAt", Query.Direction.DESCENDING)
            .limit(limit * 2)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                val all = snap?.documents?.mapNotNull { it.toObject<HikeLogDto>() } ?: emptyList()
                trySend(all.filter { !it.isPrivate }.take(limit.toInt()))
            }
        awaitClose { reg.remove() }
    }

    open fun observeHikesByCreator(uid: String): Flow<List<HikeLogDto>> = callbackFlow {
        val reg = hikes.whereEqualTo("creatorFirebaseUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject<HikeLogDto>() } ?: emptyList()
                trySend(list.sortedByDescending { it.endedAt })
            }
        awaitClose { reg.remove() }
    }

    open suspend fun upsertReview(dto: TrailReviewDto) {
        reviews.document(dto.reviewId).set(dto).await()
    }

    open suspend fun deleteReview(reviewId: String) {
        reviews.document(reviewId).delete().await()
    }

    open fun observeReviewsForHike(hikeId: String): Flow<List<TrailReviewDto>> = callbackFlow {
        val reg = reviews.whereEqualTo("hikeId", hikeId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject<TrailReviewDto>() } ?: emptyList()
                trySend(list.sortedByDescending { it.createdAt })
            }
        awaitClose { reg.remove() }
    }

    open suspend fun followUser(dto: UserFollowDto) {
        val id = "${dto.followerUid}_${dto.followeeUid}"
        follows.document(id).set(dto).await()
    }

    open suspend fun unfollowUser(followerUid: String, followeeUid: String) {
        follows.document("${followerUid}_$followeeUid").delete().await()
    }

    open suspend fun followTrail(dto: FollowedTrailDto) {
        val id = "${dto.userUid}_${dto.hikeId}"
        followedTrails.document(id).set(dto).await()
    }

    open suspend fun unfollowTrail(uid: String, hikeId: String) {
        followedTrails.document("${uid}_$hikeId").delete().await()
    }

    open suspend fun upsertComment(dto: HikeCommentDto) {
        comments.document(dto.commentId).set(dto).await()
    }

    open suspend fun deleteComment(commentId: String) {
        comments.document(commentId).delete().await()
    }

    open fun observeCommentsForHike(hikeId: String): Flow<List<HikeCommentDto>> = callbackFlow {
        val reg = comments.whereEqualTo("hikeId", hikeId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject<HikeCommentDto>() } ?: emptyList()
                trySend(list.sortedBy { it.createdAt })
            }
        awaitClose { reg.remove() }
    }

    open suspend fun fetchAchievementDefinitions(): List<AchievementDefinitionDto> =
        achievementDefs.get().await().documents.mapNotNull { it.toObject<AchievementDefinitionDto>() }

    open suspend fun upsertEarnedAchievement(dto: UserAchievementDto) {
        val id = "${dto.userUid}_${dto.achievementId}"
        userAchievements.document(id).set(dto).await()
    }

    open suspend fun upsertEmergencyContact(dto: EmergencyContactDto) {
        emergencyContacts.document(dto.contactId).set(dto).await()
    }

    open fun observeEmergencyContacts(uid: String): Flow<List<EmergencyContactDto>> = callbackFlow {
        val reg = emergencyContacts.whereEqualTo("userUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toObject<EmergencyContactDto>() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    open suspend fun deleteEmergencyContact(id: String) {
        emergencyContacts.document(id).delete().await()
    }

    open suspend fun like(dto: LikeDto) {
        likes.document("${dto.userUid}_${dto.hikeId}").set(dto).await()
    }

    open suspend fun unlike(uid: String, hikeId: String) {
        likes.document("${uid}_$hikeId").delete().await()
    }

    open fun observeLikesForHike(hikeId: String): Flow<List<LikeDto>> = callbackFlow {
        val reg = likes.whereEqualTo("hikeId", hikeId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toObject<LikeDto>() } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    private companion object {
        const val USERS = "users"
        const val HIKES = "hikes"
        const val REVIEWS = "reviews"
        const val FOLLOWS = "follows"
        const val FOLLOWED_TRAILS = "followed_trails"
        const val COMMENTS = "comments"
        const val ACHIEVEMENT_DEFS = "achievement_definitions"
        const val USER_ACHIEVEMENTS = "user_achievements"
        const val EMERGENCY_CONTACTS = "emergency_contacts"
        const val LIKES = "likes"
    }
}
