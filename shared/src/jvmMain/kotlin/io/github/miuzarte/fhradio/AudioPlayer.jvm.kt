package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.PlayerState
import okio.Path.Companion.toPath
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.Equalizer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import kotlin.math.cbrt
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

actual class AudioPlayer actual constructor(val tag: String) {

    private val factory: MediaPlayerFactory
    private val player: MediaPlayer

    init {
        val vlcPath = resolveVlcPath() ?: error("VLC 3.x not found.")
        System.setProperty("jna.library.path", vlcPath)
        factory = MediaPlayerFactory(
            "--no-video",
            "--intf=dummy",
            "--verbose=0",
            "--no-stats",
            "--no-media-library",
        )
        player = factory.mediaPlayers().newMediaPlayer()

        player.events().addMediaPlayerEventListener(
            object: MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    playBeginInstant = Clock.System.now()
                    state = state.copy(status = PlayerState.Status.Playing)
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    state = state.copy(status = PlayerState.Status.Paused)
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    state = state.copy(status = PlayerState.Status.Stopped, currentPath = null, duration = Duration.ZERO)
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    state = state.copy(status = PlayerState.Status.Ended, currentPath = null, duration = Duration.ZERO)
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    state = state.copy(status = PlayerState.Status.Error, currentPath = null, duration = Duration.ZERO)
                }

                override fun opening(mediaPlayer: MediaPlayer) {
                    state = state.copy(status = PlayerState.Status.Opening)
                }

                override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                    state = state.copy(status = PlayerState.Status.Buffering)
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    state = state.copy(position = newTime.milliseconds)
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    state = state.copy(duration = newLength.milliseconds)
                }

                override fun muted(mediaPlayer: MediaPlayer, muted: Boolean) {
                    state = state.copy(isMuted = muted)
                }

                override fun volumeChanged(mediaPlayer: MediaPlayer, volume: Float) {
                    state = state.copy(volume = volume.toInt())
                }
            },
        )
    }

    actual var state by mutableStateOf(PlayerState())
        private set

    private var playBeginInstant: Instant? = null
    private var playBeginPos: Duration = Duration.ZERO

    actual fun play(path: String, beginAt: Duration) {
        val beginMs = beginAt.inWholeMilliseconds
        player.controls().stop()
        state = state.copy(currentPath = path, position = beginAt, status = PlayerState.Status.Opening)
        playBeginInstant = null
        playBeginPos = beginAt
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
        playBeginInstant = null
        player.controls().stop()
        state = state.copy(currentPath = null, position = Duration.ZERO)
    }

    actual fun pause() = player.controls().setPause(true)
    actual fun resume() {
        playBeginInstant = Clock.System.now()
        playBeginPos = state.position
        player.controls().setPause(false)
    }

    actual fun getComputedPosition(): Duration {
        val begin = playBeginInstant ?: return state.position
        val elapsed = Clock.System.now() - begin
        val computed = playBeginPos + elapsed
        return if (state.duration > Duration.ZERO) computed.coerceAtMost(state.duration) else computed
    }

    actual fun setVolume(volume: Int): Boolean {
        val linear = (volume.coerceIn(0, 800).toDouble()) / 100.0
        val vlcVol = (cbrt(linear) * 100.0).roundToInt().coerceIn(0, 200)
        return player.audio().setVolume(vlcVol)
    }

    actual fun getVolume(): Int {
        val vlcVol = player.audio().volume().toDouble() / 100.0
        return ((vlcVol * vlcVol * vlcVol) * 100.0).roundToInt().coerceIn(0, 800)
    }

    actual fun setPreamp(db: Float) {
        if (db == 0f) {
            player.audio().setEqualizer(null)
        } else {
            val eq = Equalizer(10)
            eq.setPreamp(db)
            player.audio().setEqualizer(eq)
        }
    }

    actual fun dispose() {
        player.release()
        factory.release()
    }

    // --- helpers ---

    private fun resolveVlcPath(): String? {
        val candidates = listOf(
            "vlc", // FHRadio\
            "..".toPath() / "vlc", // FHRadio\desktopApp\
            // FHRadio\desktopApp\build\compose\binaries\main-release\app\io.github.miuzarte.fhradio
            // FHRadio\vlc
            "..".toPath() / ".." / ".." / ".." / ".." / ".." / ".." / "vlc",
            "B:".toPath() / "Program Files" / "VideoLAN" / "VLC",
            "B:".toPath() / "Program Files (x86)" / "VideoLAN" / "VLC",
            "C:".toPath() / "Program Files" / "VideoLAN" / "VLC",
            "C:".toPath() / "Program Files (x86)" / "VideoLAN" / "VLC",
            "D:".toPath() / "Program Files" / "VideoLAN" / "VLC",
            "D:".toPath() / "Program Files (x86)" / "VideoLAN" / "VLC",
        ).map { it.toString() }
        for (dir in candidates) {
            if (File(dir, "libvlc.dll").exists()) return dir
        }
        return null
    }
}
