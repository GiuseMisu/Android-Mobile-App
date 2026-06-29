package com.wildtrail.app.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioPcmDecoder {

    private const val DEQUEUE_TIMEOUT_US = 10_000L

    fun decodeToMonoFloat(file: File, targetSampleRate: Int): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return FloatArray(0)

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: return FloatArray(0)

            var channelCount = inputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1)
            var sourceSampleRate = inputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, targetSampleRate)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val pcm = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            try {
                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex)
                            val sampleSize = if (inBuf != null) {
                                extractor.readSampleData(inBuf, 0)
                            } else {
                                -1
                            }
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(
                                    inIndex, 0, sampleSize, extractor.sampleTime, 0,
                                )
                                extractor.advance()
                            }
                        }
                    }

                    when (val outIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val outFormat = codec.outputFormat
                            channelCount = outFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
                            sourceSampleRate = outFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, sourceSampleRate)
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {  }
                        else -> if (outIndex >= 0) {
                            if (info.size > 0) {
                                val outBuf = codec.getOutputBuffer(outIndex)
                                if (outBuf != null) {
                                    outBuf.position(info.offset)
                                    outBuf.limit(info.offset + info.size)
                                    val chunk = ByteArray(info.size)
                                    outBuf.get(chunk)
                                    pcm.write(chunk)
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                sawOutputEos = true
                            }
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }

            val mono = toMonoFloat(pcm.toByteArray(), channelCount.coerceAtLeast(1))
            return resampleLinear(mono, sourceSampleRate, targetSampleRate)
        } finally {
            runCatching { extractor.release() }
        }
    }

    // decoder output is interleaved 16-bit PCM; average the channels down to mono
    private fun toMonoFloat(bytes: ByteArray, channels: Int): FloatArray {
        if (bytes.isEmpty()) return FloatArray(0)
        val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalSamples = shorts.remaining()
        val frames = totalSamples / channels
        val mono = FloatArray(frames)
        var idx = 0
        for (f in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += shorts.get(idx++) / 32768f
            }
            mono[f] = sum / channels
        }
        return mono
    }

    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty() || srcRate <= 0 || srcRate == dstRate) return input
        val ratio = dstRate.toDouble() / srcRate.toDouble()
        val outLen = (input.size * ratio).toInt()
        if (outLen <= 0) return FloatArray(0)
        val out = FloatArray(outLen)
        val lastIndex = input.size - 1
        for (i in 0 until outLen) {
            val srcPos = i / ratio
            val left = srcPos.toInt()
            val frac = (srcPos - left).toFloat()
            val a = input[left.coerceIn(0, lastIndex)]
            val b = input[(left + 1).coerceIn(0, lastIndex)]
            out[i] = a + (b - a) * frac
        }
        return out
    }

    private fun MediaFormat.optInt(key: String, default: Int): Int =
        if (containsKey(key)) getInteger(key) else default
}
