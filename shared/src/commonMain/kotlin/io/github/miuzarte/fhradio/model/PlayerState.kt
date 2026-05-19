package io.github.miuzarte.fhradio.model

data class PlayerState(
    val status: PlaybackStatus,
    val currentPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isMuted: Boolean,
    val volume: Int,
)

enum class PlaybackStatus {
    Idle,
    Opening,
    Buffering,
    Playing,
    Paused,
    Stopped,
    Ended,
    Error,
}
