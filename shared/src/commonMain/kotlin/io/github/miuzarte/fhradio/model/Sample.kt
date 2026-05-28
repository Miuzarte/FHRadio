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

    val marker: List<Marker> = emptyList(),
    val loop: List<Loop> = emptyList(),
    val bpmList: List<Bpm> = emptyList(),
): Sample {

    @kotlinx.serialization.Transient
    override var parentStation: RadioStation? = null

    private fun positionOf(type: Marker.Type): Duration? =
        marker.find { it.name == type }?.position(sampleRate)

    val trackStart get() = positionOf(Marker.Type.TrackStart)
    val djDrop get() = positionOf(Marker.Type.DJDrop)
    val trackDrop get() = positionOf(Marker.Type.TrackDrop)
    val trackLoopStart get() = positionOf(Marker.Type.TrackLoopStart)
    val trackLoopEnd get() = positionOf(Marker.Type.TrackLoopEnd)
    val djSegment get() = positionOf(Marker.Type.DJSegment)
    val postDrop get() = positionOf(Marker.Type.PostDrop)
    val postRaceLoopStart get() = positionOf(Marker.Type.PostRaceLoopStart)
    val postRaceLoopEnd get() = positionOf(Marker.Type.PostRaceLoopEnd)
    val stingerStart get() = positionOf(Marker.Type.StingerStart)
    val djStart get() = positionOf(Marker.Type.DJStart)
    override val end get() = positionOf(Marker.Type.End) ?: super.end

    val bpm: Float get() = bpmList.firstOrNull()?.value ?: 0f

}

@Serializable
data class StingerSample(
    override val type: SampleType = SampleType.Stinger,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,

    val marker: List<Marker> = emptyList(),
): Sample {

    @kotlinx.serialization.Transient
    override var parentStation: RadioStation? = null

    private fun positionOf(type: Marker.Type): Duration? =
        marker.find { it.name == type }?.position(sampleRate)

    val startNextTrack get() = positionOf(Marker.Type.StartNextTrack)
    override val end get() = positionOf(Marker.Type.End) ?: super.end

}

@Serializable
data class DjSample(
    override val type: SampleType = SampleType.DJ,
    override val soundName: String,
    override val sampleLength: Int,
    override val sampleRate: Int = 48000,
    val gameEvent: String,
): Sample {

    @kotlinx.serialization.Transient
    override var parentStation: RadioStation? = null

}

@Serializable
data class Marker(
    val name: Type,
    val position: Int = -1,
) {
    fun positionMs(sampleRate: Int = 48000): Long =
        if (position > 0) position * 1_000L / sampleRate
        else position.toLong()

    fun position(sampleRate: Int = 48000): Duration =
        positionMs(sampleRate).milliseconds

    @Serializable
    enum class Type {
        // Track
        VeryStart,
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

        // Stinger
        StartNextTrack,

        // generic
        End,

    }

}

@Serializable
enum class Loop(
    val startMarker: Marker.Type,
    val endMarker: Marker.Type,
) {
    TrackMain(Marker.Type.TrackLoopStart, Marker.Type.TrackLoopEnd),
    TrackPostRace(Marker.Type.PostRaceLoopStart, Marker.Type.PostRaceLoopEnd),

}

@Serializable
data class Bpm(
    val value: Float,
    val start: Int,
) {
    fun startMs(sampleRate: Int = 48000): Long =
        if (start > 0) start * 1000L / sampleRate
        else start.toLong()

    fun start(sampleRate: Int = 48000): Duration =
        startMs(sampleRate).milliseconds

}
