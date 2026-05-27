package io.github.miuzarte.fhradio.model

import kotlin.time.Duration

data class PlayerState(
    val status: PlaybackStatus = PlaybackStatus.Idle,
    val currentPath: String? = null,
    val position: Duration = Duration.ZERO,
    val duration: Duration = Duration.ZERO,
    val isMuted: Boolean = false,
    val volume: Int = 100,
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
