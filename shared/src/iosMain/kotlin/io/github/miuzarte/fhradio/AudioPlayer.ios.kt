package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayerState
import io.github.miuzarte.fhradio.model.PlaybackStatus

actual class AudioPlayer {
    actual val state: PlayerState
        get() = PlayerState(PlaybackStatus.Idle, null, 0, 0, false, 100)

    actual fun play(path: String, beginMs: Long) = TODO("iOS AudioPlayer not yet implemented")
    actual fun tryPlay(path: String, beginMs: Long): Boolean = false
    actual fun stop() = TODO("iOS AudioPlayer not yet implemented")
    actual fun pause() = TODO("iOS AudioPlayer not yet implemented")
    actual fun resume() = TODO("iOS AudioPlayer not yet implemented")
    actual fun dispose() = TODO("iOS AudioPlayer not yet implemented")
}
