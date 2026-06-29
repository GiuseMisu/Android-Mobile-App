package com.wildtrail.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(target: File) {
        @Suppress("DEPRECATION")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
        try {
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setOutputFile(target.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = target
        } catch (t: Throwable) {
            runCatching { rec.release() }
            throw t
        }
    }

    fun stop(): File? {
        val rec = recorder ?: return null
        runCatching {
            rec.stop()
        }
        runCatching { rec.release() }
        recorder = null
        val file = outputFile
        outputFile = null
        return file
    }
}
