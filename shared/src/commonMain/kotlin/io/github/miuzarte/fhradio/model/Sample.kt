package io.github.miuzarte.fhradio.model

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface SampleSource

@Serializable
enum class SampleType: SampleSource {
    Track,
    Stinger,
    DJ,

}

@Serializable
sealed interface Sample {
    val type: SampleType
    val soundName: String
    val sampleLength: Int
    val sampleRate: Int
    val durationMs: Long get() = sampleLength * 1_000L / sampleRate
    val duration: Duration get() = durationMs.milliseconds
    val end: Duration get() = ((sampleLength - 1) * 1_000L / sampleRate).milliseconds

    @kotlinx.serialization.Transient
    var parentStation: RadioStation?

}

@Serializable
data class TrackSample(
    override val type: SampleType = SampleType.Track,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val displayName: String,
    val artist: String,
    val isXCloudModeSafe: Boolean = true,
    val markers: List<Marker> = emptyList(),
    val loops: List<LoopType> = emptyList(),
    val bpms: List<BpmEntry> = emptyList(),
): Sample {

    @kotlinx.serialization.Transient
    override var parentStation: RadioStation? = null

    private fun positionOf(type: MarkerType): Duration? =
        markers.find { it.name == type }?.position(sampleRate)

    val trackStart get() = positionOf(MarkerType.TrackStart)
    val djDrop get() = positionOf(MarkerType.DJDrop)
    val trackDrop get() = positionOf(MarkerType.TrackDrop)
    val trackLoopStart get() = positionOf(MarkerType.TrackLoopStart)
    val trackLoopEnd get() = positionOf(MarkerType.TrackLoopEnd)
    val djSegment get() = positionOf(MarkerType.DJSegment)
    val postDrop get() = positionOf(MarkerType.PostDrop)
    val postRaceLoopStart get() = positionOf(MarkerType.PostRaceLoopStart)
    val postRaceLoopEnd get() = positionOf(MarkerType.PostRaceLoopEnd)
    val stingerStart get() = positionOf(MarkerType.StingerStart)
    val djStart get() = positionOf(MarkerType.DJStart)
    override val end get() = positionOf(MarkerType.End) ?: super.end

    fun loopRangePos(type: LoopType): IntRange? {
        if (type !in loops) return null
        return (markers.find { it.name == type.startMarker }?.position ?: -1)..
                (markers.find { it.name == type.endMarker }?.position ?: -1)
    }

    val bpm: Float get() = bpms.firstOrNull()?.value ?: 0f

}

@Serializable
data class StingerSample(
    override val type: SampleType = SampleType.Stinger,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val markers: List<Marker> = emptyList(),
): Sample {

    @kotlinx.serialization.Transient
    override var parentStation: RadioStation? = null

    private fun positionOf(type: MarkerType): Duration? =
        markers.find { it.name == type }?.position(sampleRate)

    val startNextTrack get() = positionOf(MarkerType.StartNextTrack)
    override val end get() = positionOf(MarkerType.End) ?: super.end

}

@Serializable
data class DjSample(
    override val type: SampleType = SampleType.DJ,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val gameEvent: String = "",
): Sample {

    @kotlinx.serialization.Transient
    override var parentStation: RadioStation? = null

}

@Serializable
data class Marker(
    val name: MarkerType,
    val position: Int,
) {
    fun positionMs(sampleRate: Int = 48000): Long =
        position * 1_000L / sampleRate

    fun positionSec(sampleRate: Int = 48000): Double =
        position.toDouble() / sampleRate

    fun position(sampleRate: Int = 48000): Duration =
        positionMs().milliseconds

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
