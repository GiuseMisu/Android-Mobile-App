package com.wildtrail.app.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

open class ReviewImageStore(private val context: Context) {

    private fun dirFor(reviewId: String): File =
        File(File(context.filesDir, REVIEW_DIR), reviewId).apply { mkdirs() }

    open suspend fun saveReviewImages(reviewId: String, uris: List<Uri>): List<File> =
        withContext(Dispatchers.IO) {
            val dir = dirFor(reviewId)
            dir.listFiles()?.forEach { it.delete() }
            uris.mapIndexedNotNull { index, uri ->
                runCatching {
                    val target = File(dir, "img_${index}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Cannot open input stream for $uri" }
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target
                }.getOrNull()
            }
        }

    private companion object {
        const val REVIEW_DIR = "review_images"
    }
}
