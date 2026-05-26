package io.github.miuzarte.fhradio.model

import kotlinx.serialization.Serializable
import okio.Path
import okio.Path.Companion.toPath

@Serializable
data class RadioConfig(
    val version: Int,
    val timeBetweenMidTrackDJLinesSeconds: Int,
    val recentlyPlayedMaxSize: Int,
    val stations: List<RadioStation>,
)

@Serializable
data class RadioStation(
    val name: String,
    val number: Int,
    val djCharId: Int,
    val tracks: List<TrackSample> = emptyList(),
    val djSamples: List<DjSample> = emptyList(),
    val stingers: List<StingerSample> = emptyList(),
    val freeRoamPlayList: PlayList = PlayList(type = PlayListType.FreeRoam),
    val eventPlayList: PlayList = PlayList(type = PlayListType.Event),
    val shortStingerPlayList: PlayList = PlayList(type = PlayListType.ShortStinger),
) {

    // 排序后的 tracks, 用于构造 TrackSample 的路径
    val sortedTracks: List<TrackSample> by lazy { tracks.sortedBy { it.soundName } }

    fun samplesFor(source: SampleSource): List<Sample> =
        when (source) {
            SampleType.Track -> tracks
            SampleType.Stinger -> stingers
            SampleType.DJ -> djSamples

            PlayListType.FreeRoam ->
                freeRoamPlayList.entries.mapNotNull { entry ->
                    tracks.find { it.soundName == entry.name }
                }

            PlayListType.Event ->
                eventPlayList.entries.mapNotNull { entry ->
                    tracks.find { it.soundName == entry.name }
                }

            PlayListType.ShortStinger ->
                shortStingerPlayList.entries.mapNotNull { entry ->
                    stingers.find { it.soundName == entry.name }
                }
        }

    fun randomFor(
        source: SampleSource,
        exclude: Set<Sample> = emptySet(),
    ): Sample? {
        val samples = samplesFor(source)
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: samplesFor(source) // 回退: 过滤后全空
        return samples.randomOrNull()
    }

    fun randomTrack(exclude: Set<TrackSample> = emptySet()): TrackSample? {
        val samples = tracks
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: tracks
        return samples.randomOrNull()
    }

    fun randomStinger(exclude: Set<StingerSample> = emptySet()): StingerSample? {
        val samples = stingers
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: stingers
        return samples.randomOrNull()
    }

    fun randomDj(exclude: Set<DjSample> = emptySet()): DjSample? {
        val samples = djSamples
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: djSamples
        return samples.randomOrNull()
    }

    val allSamples: List<Sample>
        get() = tracks + djSamples + stingers

    // 返回相对路径, 无扩展名
    // "Gacha City Radio/Track/sound_0"
    // "Gacha City Radio/Stinger/sound_1"
    // "Gacha City Radio/DJ/sound_2"
    fun pathFor(sample: Sample): Path? {
        val (sampleDir, idx) = when (sample) {
            is TrackSample -> SampleType.Track.toString() to sortedTracks.indexOf(sample)
            is StingerSample -> SampleType.Stinger.toString() to stingers.indexOf(sample)
            is DjSample -> SampleType.DJ.toString() to djSamples.indexOf(sample)
        }
        if (idx < 0) return null
        return name.toPath() / sampleDir / "sound_$idx"
    }

}
