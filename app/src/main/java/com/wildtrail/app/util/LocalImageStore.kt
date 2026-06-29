package com.wildtrail.app.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

open class LocalImageStore(private val context: Context) {

    private val dir: File
        get() = File(context.filesDir, PROFILE_DIR).apply { mkdirs() }

    open suspend fun saveProfilePicture(uid: String, srcUri: Uri): File =
        withContext(Dispatchers.IO) {
            val target = File(dir, "profile_${uid}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(srcUri).use { input ->
                requireNotNull(input) { "Cannot open input stream for $srcUri" }
                target.outputStream().use { output -> input.copyTo(output) }
            }
            pruneOldPictures(uid, keep = target.name)
            target
        }

    private fun pruneOldPictures(uid: String, keep: String) {
        dir.listFiles()
            ?.filter { it.name.startsWith("profile_${uid}_") && it.name != keep }
            ?.forEach { it.delete() }
    }

    private companion object {
        const val PROFILE_DIR = "profile_pictures"
    }
}
