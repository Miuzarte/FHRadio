package io.github.miuzarte.fhradio.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


@Serializable
enum class SampleType {
    Track,
    Stinger,
    DJ,
}

@Serializable
sealed interface Sample {
    val type: SampleType
    val audioFilePath: String?
    val soundName: String
    val sampleLength: Int
    val sampleRate: Int
    val durationMs: Long get() = sampleLength * 1000L / (sampleRate)
    val duration: Duration get() = durationMs.milliseconds
}

@Serializable
data class TrackSample(
    override val type: SampleType = SampleType.Track,
    override val audioFilePath: String? = null,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val displayName: String,
    val artist: String,
    val isXCloudModeSafe: Boolean = true,
    val markers: List<Marker> = emptyList(),
    val loops: List<LoopType> = emptyList(),
    val bpms: List<BpmEntry> = emptyList(),
) : Sample {

    fun positionOf(type: MarkerType): Int =
        markers.find { it.name == type }?.position ?: -1

    val trackStart: Int get() = positionOf(MarkerType.TrackStart)
    val djDrop: Int get() = positionOf(MarkerType.DJDrop)
    val trackDrop: Int get() = positionOf(MarkerType.TrackDrop)
    val trackLoopStart: Int get() = positionOf(MarkerType.TrackLoopStart)
    val trackLoopEnd: Int get() = positionOf(MarkerType.TrackLoopEnd)
    val djSegment: Int get() = positionOf(MarkerType.DJSegment)
    val postDrop: Int get() = positionOf(MarkerType.PostDrop)
    val postRaceLoopStart: Int get() = positionOf(MarkerType.PostRaceLoopStart)
    val postRaceLoopEnd: Int get() = positionOf(MarkerType.PostRaceLoopEnd)
    val stingerStart: Int get() = positionOf(MarkerType.StingerStart)
    val djStart: Int get() = positionOf(MarkerType.DJStart)
    val end: Int get() = positionOf(MarkerType.End)

    fun loopRange(type: LoopType): IntRange? {
        if (type !in loops) return null
        return positionOf(type.startMarker)..positionOf(type.endMarker)
    }

    val bpm: Float get() = bpms.firstOrNull()?.value ?: 0f
}

@Serializable
data class StingerSample(
    override val type: SampleType = SampleType.Stinger,
    override val audioFilePath: String? = null,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val markers: List<Marker> = emptyList(),
) : Sample {
    private fun positionOf(type: MarkerType): Int =
        markers.find { it.name == type }?.position ?: -1

    val startNextTrack: Int get() = positionOf(MarkerType.StartNextTrack)
    val end: Int get() = positionOf(MarkerType.End)
}

@Serializable
data class DjSample(
    override val type: SampleType = SampleType.DJ,
    override val audioFilePath: String? = null,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val gameEvent: String = "",
) : Sample

@Serializable
data class Marker(
    val name: MarkerType,
    val position: Int,
) {
    fun positionMs(sampleRate: Int = 48000): Long =
        position * 1000L / sampleRate

    fun positionSec(sampleRate: Int = 48000): Double =
        position.toDouble() / sampleRate
}

@Serializable
data class BpmEntry(
    val value: Float,
    val start: Int,
) {
    fun startMs(sampleRate: Int = 48000): Long =
        start * 1000L / sampleRate
}

@Serializable
enum class MarkerType {
    TrackStart,
    DJDrop,
    TrackDrop,
    TrackLoopStart,
    TrackLoopEnd,
    DJSegment,
    PostDrop,
    PostRaceLoopStart,
    PostRaceLoopEnd,
    StingerStart,
    DJStart,
    End,
    StartNextTrack,
}

@Serializable
enum class LoopType(
    val startMarker: MarkerType,
    val endMarker: MarkerType,
) {
    TrackMain(MarkerType.TrackLoopStart, MarkerType.TrackLoopEnd),
    TrackPostRace(MarkerType.PostRaceLoopStart, MarkerType.PostRaceLoopEnd),
}
