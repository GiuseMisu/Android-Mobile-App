package com.wildtrail.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

data class BirdDetection(
    val commonName: String,
    val scientificName: String,
    val confidence: Float,
)

class BirdNetClassifier(private val context: Context) {

    // Interpreter isn't thread-safe; serialise inference through this
    private val mutex = Mutex()

    fun isModelInstalled(): Boolean = runCatching {
        val dir = MODEL_ASSET.substringBeforeLast('/')
        val names = context.assets.list(dir).orEmpty()
        MODEL_ASSET.substringAfterLast('/') in names &&
            LABELS_ASSET.substringAfterLast('/') in names
    }.getOrDefault(false)

    private val interpreter: Interpreter by lazy {
        Interpreter(loadModelAsset(), Interpreter.Options().apply { setNumThreads(4) })
    }

    private val labels: List<String> by lazy {
        context.assets.open(LABELS_ASSET).bufferedReader().useLines { seq ->
            seq.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    suspend fun detect(file: File, topN: Int = 5): List<BirdDetection> =
        withContext(Dispatchers.Default) {
            try {
                detectInternal(file, topN)
            } catch (t: Throwable) {
                Log.e(TAG, "BirdNET analysis failed for ${file.name}", t)
                throw t
            }
        }

    private suspend fun detectInternal(file: File, topN: Int): List<BirdDetection> {
        val samples = AudioPcmDecoder.decodeToMonoFloat(file, SAMPLE_RATE)
        if (samples.isEmpty()) return emptyList()

        return mutex.withLock {
                val windowSize = interpreter.getInputTensor(0).shape().last()
                val numClasses = interpreter.getOutputTensor(0).shape().last()
                val usableClasses = minOf(numClasses, labels.size)

                val maxConfidence = FloatArray(numClasses)
                val input = ByteBuffer
                    .allocateDirect(windowSize * 4)
                    .order(ByteOrder.nativeOrder())
                val output = Array(1) { FloatArray(numClasses) }

                var start = 0
                while (start < samples.size) {
                    input.clear()
                    for (i in 0 until windowSize) {
                        val s = start + i
                        input.putFloat(if (s < samples.size) samples[s] else 0f)
                    }
                    input.rewind()

                    interpreter.run(input, output)

                    val row = output[0]
                    for (c in 0 until numClasses) {
                        val conf = if (APPLY_SIGMOID) sigmoid(row[c]) else row[c]
                        if (conf > maxConfidence[c]) maxConfidence[c] = conf
                    }

                    if (samples.size <= windowSize) break
                    start += windowSize
                }

                (0 until usableClasses)
                    .asSequence()
                    .map { idx -> idx to maxConfidence[idx] }
                    .filter { it.second >= MIN_CONFIDENCE }
                    .sortedByDescending { it.second }
                    .take(topN)
                    .map { (idx, conf) -> toDetection(labels[idx], conf) }
                    .toList()
            }
        }

    private fun toDetection(label: String, confidence: Float): BirdDetection {
        val parts = label.split('_', limit = 2)
        return if (parts.size == 2) {
            BirdDetection(commonName = parts[1].trim(), scientificName = parts[0].trim(), confidence = confidence)
        } else {
            BirdDetection(commonName = label, scientificName = "", confidence = confidence)
        }
    }

    private fun loadModelAsset(): MappedByteBuffer {
        val fd = context.assets.openFd(MODEL_ASSET)
        FileInputStream(fd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

    private companion object {
        const val TAG = "BirdNet"

        const val SAMPLE_RATE = 48_000
        const val MODEL_ASSET = "birdnet/model.tflite"
        const val LABELS_ASSET = "birdnet/labels.txt"

        const val MIN_CONFIDENCE = 0.10f

        // model emits logits; set false if your export already applies the sigmoid
        const val APPLY_SIGMOID = true
    }
}
