package io.github.miuzarte.fhradio

import androidx.compose.runtime.mutableStateOf
import io.github.miuzarte.fhradio.model.*
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.*
import java.io.File

actual class AudioPlayer {

    private val factory: MediaPlayerFactory
    private val player: MediaPlayer

    private val _state = mutableStateOf(
        PlayerState(
            status = PlaybackStatus.Idle,
            currentPath = null,
            positionMs = 0L,
            durationMs = 0L,
            isMuted = false,
            volume = 100,
        )
    )

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
                _state.value = _state.value.copy(status = PlaybackStatus.Playing)
            }
            override fun paused(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(status = PlaybackStatus.Paused)
            }
            override fun stopped(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(status = PlaybackStatus.Stopped, currentPath = null, durationMs = 0)
            }
            override fun finished(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(status = PlaybackStatus.Ended, currentPath = null, durationMs = 0)
            }
            override fun error(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(status = PlaybackStatus.Error, currentPath = null, durationMs = 0)
            }
            override fun opening(mediaPlayer: MediaPlayer) {
                _state.value = _state.value.copy(status = PlaybackStatus.Opening)
            }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _state.value = _state.value.copy(status = PlaybackStatus.Buffering)
            }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _state.value = _state.value.copy(positionMs = newTime)
            }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                _state.value = _state.value.copy(durationMs = newLength)
            }
            override fun muted(mediaPlayer: MediaPlayer, muted: Boolean) {
                _state.value = _state.value.copy(isMuted = muted)
            }
            override fun volumeChanged(mediaPlayer: MediaPlayer, volume: Float) {
                _state.value = _state.value.copy(volume = volume.toInt())
            }
        })
    }

    actual fun getState(): PlayerState = _state.value

    actual fun play(path: String) {
        player.controls().stop()
        _state.value = _state.value.copy(currentPath = path, positionMs = 0)
        player.media().play(path)
    }

    actual fun play(path: String, beginMs: Long) {
        player.controls().stop()
        _state.value = _state.value.copy(currentPath = path, positionMs = beginMs)
        player.media().play(path)
        if (beginMs > 0) {
            player.controls().setTime(beginMs)
        }
    }

    actual fun tryPlay(path: String): Boolean {
        val status = _state.value.status
        if (status == PlaybackStatus.Playing ||
            status == PlaybackStatus.Buffering ||
            status == PlaybackStatus.Opening) return false
        play(path)
        return true
    }

    actual fun tryPlay(path: String, beginMs: Long): Boolean {
        val status = _state.value.status
        if (status == PlaybackStatus.Playing ||
            status == PlaybackStatus.Buffering ||
            status == PlaybackStatus.Opening) return false
        play(path, beginMs)
        return true
    }

    actual fun stop() {
        player.controls().stop()
        _state.value = _state.value.copy(currentPath = null, positionMs = 0)
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
