package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayerState
import kotlin.time.Duration

expect class AudioPlayer() {
    var state: PlayerState
        private set

    fun play(path: String, beginAt: Duration = Duration.ZERO)
    fun tryPlay(path: String, beginAt: Duration = Duration.ZERO): Boolean
    fun stop()
    fun pause()
    fun resume()
    fun dispose()
}
