package io.github.miuzarte.fhradio.model

data class PlayerState(
    val status: PlaybackStatus,
    val currentPath: String?,
    val positionMs: Long,
    val durationMs: Long,
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
