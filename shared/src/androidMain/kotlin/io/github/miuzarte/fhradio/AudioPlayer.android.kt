package io.github.miuzarte.fhradio

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.miuzarte.fhradio.model.PlayerState
import kotlinx.coroutines.*
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

actual class AudioPlayer actual constructor(val tag: String) {

    private val player = ExoPlayer.Builder(AndroidBridge.appContext).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var positionJob: Job? = null

    private var volumeScale = 1f
    private var baseVolume = 100

    init {
        player.addListener(
            object: Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_IDLE ->
                            setState(PlayerState.Status.Idle)

                        Player.STATE_BUFFERING ->
                            setState(PlayerState.Status.Buffering)

                        Player.STATE_READY ->
                            setState(
                                if (player.playWhenReady) PlayerState.Status.Playing
                                else PlayerState.Status.Paused,
                            )

                        Player.STATE_ENDED ->
                            setState(PlayerState.Status.Ended)
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (player.playbackState == Player.STATE_READY)
                        setState(
                            if (playWhenReady) PlayerState.Status.Playing
                            else PlayerState.Status.Paused,
                        )

                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        playBeginInstant = Clock.System.now()
                        setState(PlayerState.Status.Playing)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    setState(PlayerState.Status.Error)
                    player.stop()
                }
            },
        )
    }

    actual var state by mutableStateOf(PlayerState())
        private set

    private var playBeginInstant: Instant? = null
    private var playBeginPos: Duration = Duration.ZERO

    actual fun play(path: String, beginAt: Duration) {
        positionJob?.cancel()
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
        playBeginInstant = null
        playBeginPos = beginAt
        this.state = state.copy(
            currentPath = path,
            position = beginAt,
            status = PlayerState.Status.Opening,
        )
        startPositionPolling()
    }

    actual fun tryPlay(path: String, beginAt: Duration): Boolean {
        if (state.isBusy) return false
        play(path, beginAt)
        return true
    }

    actual fun stop() {
        playBeginInstant = null
        positionJob?.cancel()
        positionJob = null
        player.stop()
        this.state = state.copy(
            status = PlayerState.Status.Stopped,
            currentPath = null,
            position = Duration.ZERO,
        )
    }

    actual fun pause() {
        positionJob?.cancel()
        positionJob = null
        player.pause()
    }

    actual fun resume() {
        playBeginInstant = Clock.System.now()
        playBeginPos = player.currentPosition.milliseconds.coerceAtLeast(Duration.ZERO)
        player.play()
        startPositionPolling()
    }

    actual fun getComputedPosition(): Duration {
        val begin = playBeginInstant ?: return state.position
        val elapsed = Clock.System.now() - begin
        val computed = playBeginPos + elapsed
        return if (state.duration > Duration.ZERO) computed.coerceAtMost(state.duration) else computed
    }

    actual fun setVolume(volume: Int): Boolean {
        baseVolume = volume
        player.volume = volume / 100f * volumeScale
        return true
    }

    actual fun getVolume(): Int = baseVolume

    actual fun setPreamp(db: Float) {
        volumeScale = 10f.pow(db / 20f)
        player.volume = baseVolume / 100f * volumeScale
    }

    actual fun dispose() {
        positionJob?.cancel()
        scope.cancel()
        player.release()
    }

    // --- helpers ---

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val pos = player.currentPosition
                if (pos >= 0) {
                    this@AudioPlayer.state =
                        this@AudioPlayer.state.copy(position = pos.milliseconds)
                }
                delay(20.milliseconds)
            }
        }
    }

    private fun setState(status: PlayerState.Status) {
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
