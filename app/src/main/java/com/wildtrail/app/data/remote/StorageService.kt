package com.wildtrail.app.data.remote

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

open class StorageService(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) {

    open suspend fun uploadProfilePicture(uid: String, localUri: Uri): String {
        val ref = storage.reference.child("profile_pictures/$uid.jpg")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    open suspend fun uploadReviewImage(reviewId: String, index: Int, localUri: Uri): String {
        val ref = storage.reference.child("review_images/$reviewId/$index.jpg")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Uploads a hike's captured photo/audio so other users can see and play it.
     * [ext] is the file extension (e.g. "jpg" or "m4a"). Returns the public download URL.
     */
    open suspend fun uploadHikeMedia(
        hikeId: String,
        mediaId: String,
        ext: String,
        localUri: Uri,
    ): String {
        val ref = storage.reference.child("hike_media/$hikeId/$mediaId.$ext")
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    /** Best-effort cleanup of an uploaded file given its download URL. */
    open suspend fun deleteByUrl(downloadUrl: String) {
        storage.getReferenceFromUrl(downloadUrl).delete().await()
    }
}
