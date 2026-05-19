package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayerState
import io.github.miuzarte.fhradio.model.PlaybackStatus

actual class AudioPlayer {
    actual fun getState(): PlayerState = PlayerState(PlaybackStatus.Idle, null, 0, 0, false, 100)
    actual fun play(path: String) = TODO("Android AudioPlayer not yet implemented")
    actual fun play(path: String, beginMs: Long) = TODO("Android AudioPlayer not yet implemented")
    actual fun tryPlay(path: String): Boolean = false
    actual fun tryPlay(path: String, beginMs: Long): Boolean = false
    actual fun stop() = TODO("Android AudioPlayer not yet implemented")
    actual fun pause() = TODO("Android AudioPlayer not yet implemented")
    actual fun resume() = TODO("Android AudioPlayer not yet implemented")
    actual fun dispose() = TODO("Android AudioPlayer not yet implemented")
}
