package io.github.miuzarte.fhradio

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.miuzarte.fhradio.model.PlaybackStatus
import io.github.miuzarte.fhradio.model.PlayerState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

actual class AudioPlayer {

    private val player = ExoPlayer.Builder(AndroidBridge.activity).build()

    init {
        player.addListener(
            object: Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_IDLE ->
                            setState(PlaybackStatus.Idle)

                        Player.STATE_BUFFERING ->
                            setState(PlaybackStatus.Buffering)

                        Player.STATE_READY ->
                            setState(
                                if (player.playWhenReady) PlaybackStatus.Playing
                                else PlaybackStatus.Paused,
                            )

                        Player.STATE_ENDED ->
                            setState(PlaybackStatus.Ended)
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (player.playbackState == Player.STATE_READY)
                        setState(
                            if (playWhenReady) PlaybackStatus.Playing
                            else PlaybackStatus.Paused,
                        )

                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying)
                        setState(PlaybackStatus.Playing)
                }

                override fun onPlayerError(error: PlaybackException) {
                    setState(PlaybackStatus.Error)
                    player.stop()
                }
            },
        )
    }

    actual var state by mutableStateOf(PlayerState())
        private set

    actual fun play(path: String, beginAt: Duration) {
        player.stop()
        val mediaItem =
            if (path.startsWith("content://")) MediaItem.fromUri(Uri.parse(path))
            else MediaItem.fromUri(path)
        player.setMediaItem(mediaItem)
        player.prepare()
        if (beginAt > Duration.ZERO) {
            player.seekTo(beginAt.inWholeMilliseconds)
        }
        player.play()
        this.state = state.copy(
            currentPath = path,
            position = beginAt,
            status = PlaybackStatus.Opening,
        )
    }

    actual fun tryPlay(path: String, beginAt: Duration): Boolean {
        if (state.isBusy) return false
        play(path, beginAt)
        return true
    }

    actual fun stop() {
        player.stop()
        this.state = state.copy(
            status = PlaybackStatus.Stopped,
            currentPath = null,
            position = Duration.ZERO,
        )
    }

    actual fun pause() = player.pause()
    actual fun resume() = player.play()

    actual fun setVolume(volume: Int): Boolean {
        player.volume = volume.toFloat() / 100f
        return true
    }

    actual fun getVolume(): Int = (player.volume * 100).toInt()

    actual fun dispose() {
        player.release()
    }

    private fun setState(status: PlaybackStatus) {
        val pos =
            if (player.currentPosition >= 0) player.currentPosition.milliseconds
            else Duration.ZERO
        val dur =
            if (player.duration > 0) player.duration.milliseconds
            else state.duration
        this.state = state.copy(
            status = status,
            position = pos,
            duration = dur,
        )
    }
}
