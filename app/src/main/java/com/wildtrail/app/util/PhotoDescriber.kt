package com.wildtrail.app.util

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class PhotoDescriber {

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.55f)
                .build(),
        )
    }

    suspend fun describe(file: File): String = withContext(Dispatchers.Default) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: return@withContext "Couldn't read this photo"
        val image = InputImage.fromBitmap(bitmap, 0)
        val labels = labeler.process(image).await()
        formatLabels(labels)
    }

    private fun formatLabels(labels: List<ImageLabel>): String {
        if (labels.isEmpty()) return "Nothing obvious detected — could be a scene or distant view."
        val top = labels.sortedByDescending { it.confidence }
            .take(3)
            .joinToString(", ") { it.text }
        return "Looks like: $top"
    }
}
