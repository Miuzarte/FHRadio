package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.PlaybackStatus
import io.github.miuzarte.fhradio.model.PlayerState
import kotlin.time.Duration

actual class AudioPlayer actual constructor(val tag: String) {
    actual var state by mutableStateOf(
        PlayerState(
            status = PlaybackStatus.Idle,
            currentPath = null,
            position = Duration.ZERO,
            duration = Duration.ZERO,
            isMuted = false,
            volume = 100,
        ),
    )
        private set

    actual fun play(path: String, beginAt: Duration) {
        TODO("iOS AudioPlayer not yet implemented")
    }

    actual fun tryPlay(path: String, beginAt: Duration): Boolean = false
    actual fun stop() {
        TODO("iOS AudioPlayer not yet implemented")
    }

    actual fun pause() {
        TODO("iOS AudioPlayer not yet implemented")
    }

    actual fun resume() {
        TODO("iOS AudioPlayer not yet implemented")
    }

    actual fun setVolume(volume: Int): Boolean = TODO("iOS AudioPlayer not yet implemented")
    actual fun getVolume(): Int = TODO("iOS AudioPlayer not yet implemented")
    actual fun dispose() {
        TODO("iOS AudioPlayer not yet implemented")
    }
}
