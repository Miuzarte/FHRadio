package io.github.miuzarte.fhradio.model

import okio.Path
import okio.Path.Companion.toPath

data class RadioInfo(
    val version: Int, // FH6: 2, FH4: 1
    val timeBetweenMidTrackDJLinesSeconds: Int, // 120
    val recentlyPlayedMaxSize: Int, // -1

    val stations: List<RadioStation>,
)

data class RadioStation(
    val name: String,
    val number: Int,
    val djCharId: Int, // version2

    val banks: List<Bank> = emptyList(),

    val track: List<TrackSample> = emptyList(),
    val dj: List<DjSample> = emptyList(),
    val stinger: List<StingerSample> = emptyList(),

    val freeRoam: PlayList = PlayList(type = PlayListType.FreeRoam),
    val event: PlayList = PlayList(type = PlayListType.Event),
    val shortStinger: PlayList = PlayList(type = PlayListType.ShortStinger),
) {

    // 排序后的 tracks, 用于构造 TrackSample 的路径
    val sortedTracks: List<TrackSample> by lazy { track.sortedBy { it.soundName } }

    fun samplesFor(source: SampleSource): List<Sample> =
        when (source) {
            SampleType.Track -> track
            SampleType.Stinger -> stinger
            SampleType.DJ -> dj

            PlayListType.FreeRoam ->
                freeRoam.entries.mapNotNull { entry ->
                    track.find { it.soundName == entry.name }
                }

            PlayListType.Event ->
                event.entries.mapNotNull { entry ->
                    track.find { it.soundName == entry.name }
                }

            PlayListType.ShortStinger ->
                shortStinger.entries.mapNotNull { entry ->
                    stinger.find { it.soundName == entry.name }
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
        val samples = track
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: track
        return samples.randomOrNull()
    }

    fun randomStinger(exclude: Set<StingerSample> = emptySet()): StingerSample? {
        val samples = stinger
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: stinger
        return samples.randomOrNull()
    }

    fun randomDj(exclude: Set<DjSample> = emptySet()): DjSample? {
        val samples = dj
            .filterNot { it in exclude }
            .takeIf { it.isNotEmpty() }
            ?: dj
        return samples.randomOrNull()
    }

    val allSamples: List<Sample>
        get() = track + dj + stinger

    fun playableTracks(excludedSuffixes: Set<String>): List<TrackSample> {
        if (excludedSuffixes.isEmpty()) return track
        return track.filterNot { t -> excludedSuffixes.any { t.soundName.endsWith(it) } }
    }

    // 跨电台虚拟电台 (如 Streamer Mode)
    // 所有曲目都属于其他电台, 自身无音频文件
    val isCrossStation: Boolean
        get() = track.isNotEmpty() && track.all { it.parentStation != null }

    // 返回相对路径, 无扩展名
    // "Horizon Pulse/Track/CU1/sound_0"  (有 BankMapping 映射)
    // "Horizon Pulse/Track/DISK/sound_3" (有 BankMapping 映射)
    // "Gacha City Radio/Track/sound_0"   (无映射, 回退到 sortedTracks)
    // "Gacha City Radio/Stinger/sound_1"
    // "Gacha City Radio/DJ/sound_2"
    // 虚拟电台 (Streamer Mode) 自动委托到 parentStation
    fun pathFor(sample: Sample): Path? {
        val station = sample.parentStation ?: this
        when (sample) {
            is TrackSample -> {
                val mapping = BankMapping.lookup(sample.soundName)
                if (mapping != null) {
                    val (bankName, idx) = mapping
                    return station.name.toPath() / SampleType.Track.toString() / bankName / "sound_$idx"
                }
                val idx = station.sortedTracks.indexOfFirst { it.soundName == sample.soundName }
                if (idx < 0) return null
                return station.name.toPath() / SampleType.Track.toString() / "sound_$idx"
            }

            is StingerSample -> {
                val idx = station.stinger.indexOfFirst { it.soundName == sample.soundName }
                if (idx < 0) return null
                return station.name.toPath() / SampleType.Stinger.toString() / "sound_$idx"
            }

            is DjSample -> {
                val idx = station.dj.indexOfFirst { it.soundName == sample.soundName }
                if (idx < 0) return null
                return station.name.toPath() / SampleType.DJ.toString() / "sound_$idx"
            }
        }
    }
}

data class Bank(val name: String)
