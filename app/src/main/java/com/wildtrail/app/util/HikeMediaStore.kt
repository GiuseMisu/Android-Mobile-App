package com.wildtrail.app.util

import android.content.Context
import android.graphics.Bitmap
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class HikeMediaStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, MEDIA_DIR).apply { mkdirs() }

    private val cacheDir: File
        get() = File(context.cacheDir, MEDIA_CACHE_DIR).apply { mkdirs() }

    fun newAudioFile(): File = File(dir, "audio_${System.currentTimeMillis()}.m4a")

    suspend fun savePhoto(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val target = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        target
    }

    /**
     * Returns a readable local [File] for a media item. On-device items return their file
     * directly; remote (http) items are downloaded into the cache once and reused after.
     * Lets BirdNet / photo description work for media that lives on Firebase Storage.
     */
    suspend fun localFileFor(item: HikeMediaItem): File = withContext(Dispatchers.IO) {
        val path = item.filePath
        if (!path.startsWith("http")) return@withContext File(path)
        val ext = if (item.type == HikeMediaType.AUDIO) "m4a" else "jpg"
        val cached = File(cacheDir, "${item.id}.$ext")
        if (cached.exists() && cached.length() > 0L) return@withContext cached
        URL(path).openStream().use { input ->
            FileOutputStream(cached).use { output -> input.copyTo(output) }
        }
        cached
    }

    private companion object {
        const val MEDIA_DIR = "hike_media"
        const val MEDIA_CACHE_DIR = "hike_media_cache"
        const val JPEG_QUALITY = 88
    }
}
