package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayerState
import kotlin.time.Duration

expect class AudioPlayer() {
    fun getState(): PlayerState
    fun play(path: String)
    fun play(path: String, beginAt: Duration)
    fun tryPlay(path: String): Boolean
    fun tryPlay(path: String, beginAt: Duration): Boolean
    fun stop()
    fun pause()
    fun resume()
    fun dispose()
}
