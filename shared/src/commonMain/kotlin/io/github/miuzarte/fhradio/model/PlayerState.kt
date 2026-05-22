package io.github.miuzarte.fhradio.model

import kotlin.time.Duration

data class PlayerState(
    val status: PlaybackStatus,
    val currentPath: String?,
    val position: Duration,
    val duration: Duration,
    val isMuted: Boolean,
    val volume: Int,
) {
    val isBusy: Boolean
        get() = status.isBusy
}

enum class PlaybackStatus {
    Idle,
    Opening,
    Buffering,
    Playing,
    Paused,
    Stopped,
    Ended,
    Error;

    val isBusy: Boolean
        get() = when (this) {
            Idle,
                -> false

            Opening,
            Buffering,
            Playing,
                -> true

            Paused,
            Stopped,
            Ended,
            Error,
                -> false
        }
}
