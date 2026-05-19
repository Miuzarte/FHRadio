package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayerState

expect class AudioPlayer() {
    fun getState(): PlayerState
    fun play(path: String)
    fun play(path: String, beginMs: Long)
    fun tryPlay(path: String): Boolean
    fun tryPlay(path: String, beginMs: Long): Boolean
    fun stop()
    fun pause()
    fun resume()
    fun dispose()
}
