package com.wildtrail.app.util

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class AudioPlayerController {
    private var player: MediaPlayer? = null
    var playingPath: String? by mutableStateOf(null)
        private set

    fun toggle(path: String) {
        if (playingPath == path && player?.isPlaying == true) {
            player?.pause()
            playingPath = null
            return
        }
        stop()
        val remote = path.startsWith("http")
        player = MediaPlayer().apply {
            setOnCompletionListener { this@AudioPlayerController.stop() }
            setDataSource(path)
            if (remote) {
                // Network prepare must not block the main thread — start once ready.
                setOnPreparedListener { it.start() }
                prepareAsync()
            } else {
                prepare()
                start()
            }
        }
        playingPath = path
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingPath = null
    }
}

@Composable
fun rememberAudioPlayerController(): AudioPlayerController {
    val controller = remember { AudioPlayerController() }
    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    return controller
}
