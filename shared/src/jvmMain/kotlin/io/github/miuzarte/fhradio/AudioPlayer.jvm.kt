package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.*
import kotlin.time.Duration
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.*
import java.io.File

actual class AudioPlayer {

    private val factory: MediaPlayerFactory
    private val player: MediaPlayer

    init {
        val vlcPath = resolveVlcPath()
            ?: error("VLC 3.x not found. Put it in desktopApp\\vlc-3.0.23\\ or install to C:\\Program Files\\VideoLAN\\VLC")
        System.setProperty("jna.library.path", vlcPath)
        factory = MediaPlayerFactory(
            "--no-video",
            "--intf=dummy",
            "--verbose=0",
            "--no-stats",
            "--no-media-library"
        )
        player = factory.mediaPlayers().newMediaPlayer()

        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                state = state.copy(status = PlaybackStatus.Playing)
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                state = state.copy(status = PlaybackStatus.Paused)
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                state = state.copy(status = PlaybackStatus.Stopped, currentPath = null, durationMs = 0)
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                state = state.copy(status = PlaybackStatus.Ended, currentPath = null, durationMs = 0)
            }

            override fun error(mediaPlayer: MediaPlayer) {
                state = state.copy(status = PlaybackStatus.Error, currentPath = null, durationMs = 0)
            }

            override fun opening(mediaPlayer: MediaPlayer) {
                state = state.copy(status = PlaybackStatus.Opening)
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                state = state.copy(status = PlaybackStatus.Buffering)
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                state = state.copy(positionMs = newTime)
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                state = state.copy(durationMs = newLength)
            }

            override fun muted(mediaPlayer: MediaPlayer, muted: Boolean) {
                state = state.copy(isMuted = muted)
            }

            override fun volumeChanged(mediaPlayer: MediaPlayer, volume: Float) {
                state = state.copy(volume = volume.toInt())
            }
        })
    }

    actual var state by mutableStateOf(
        PlayerState(
            status = PlaybackStatus.Idle,
            currentPath = null,
            positionMs = 0L,
            durationMs = 0L,
            isMuted = false,
            volume = 100,
        )
    )
        private set

    actual fun play(path: String, beginAt: Duration) {
        val beginMs = beginAt.inWholeMilliseconds
        player.controls().stop()
        state = state.copy(currentPath = path, positionMs = beginMs)
        player.media().play(path)
        if (beginMs > 0) {
            player.controls().setTime(beginMs)
        }
    }

    actual fun tryPlay(path: String, beginAt: Duration): Boolean {
        if (state.isBusy) return false
        play(path, beginAt)
        return true
    }

    actual fun stop() {
        player.controls().stop()
        state = state.copy(currentPath = null, positionMs = 0)
    }

    actual fun pause() = player.controls().setPause(true)
    actual fun resume() = player.controls().setPause(false)
    actual fun dispose() {
        player.release()
        factory.release()
    }

    // --- helpers ---

    private fun resolveVlcPath(): String? {
        val candidates = listOf(
            "desktopApp\\vlc-3.0.23",
            "B:\\Git\\FHRadio\\desktopApp\\vlc-3.0.23",
            "C:\\Program Files\\VideoLAN\\VLC",
            "C:\\Program Files (x86)\\VideoLAN\\VLC",
        )
        for (dir in candidates) {
            if (File(dir, "libvlc.dll").exists()) return dir
        }
        return null
    }
}
