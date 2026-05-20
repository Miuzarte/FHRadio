package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.DjSample
import io.github.miuzarte.fhradio.model.PlayMode
import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.model.StingerSample
import io.github.miuzarte.fhradio.model.TrackSample
import kotlin.random.Random
import kotlin.time.Duration


class PlayerEngine(
    station: RadioStation,
    val playMode: PlayMode, // shuffle / order
    val crossLists: List<SampleType>,
    val patternEnabled: Boolean,
    val patternNodes: List<PatternNode>,
) : RadioModeEngine(station) {

    private val usePatternMode get() = patternEnabled && patternNodes.isNotEmpty()

    private data class PlaylistKey(
        val stationName: String,
        val playMode: PlayMode,
        val crossLists: List<SampleType>,
        val patternEnabled: Boolean,
        val patternNodes: List<PatternNode>,
    )

    private var playlistBuiltFor: PlaylistKey? = null // 判断列表是否需要重新构建
    private val playlist = mutableListOf<Sample>() // 构建好的播放列表 (非调试时对用户隐藏)
    private var playlistIndex = 0 // 播放到的地方

    override fun reset() {
        // TODO
    }

    // 构建列表再返回
    override fun getPlaylist(): Pair<List<Sample>, Int>? {
        buildPlaylist()
        return if (playlist.isEmpty()) null
        else playlist.toList() to playlistIndex
    }

    // 按需构建播放列表, 默认不强制
    private fun buildPlaylist(purge: Boolean = false) {
        val key = PlaylistKey(
            stationName = station.name,
            playMode = playMode,
            crossLists = crossLists,
            patternEnabled = patternEnabled,
            patternNodes = patternNodes,
        )
        if (key == playlistBuiltFor && !purge) return
        playlistBuiltFor = key

        // 重新构建
        // TODO: 实现 LoopPattern
        playlist.clear()
        playlistIndex = 0

        val allSamples = crossLists.flatMap { type ->
            val list = when (type) {
                SampleType.Track -> station.tracks
                SampleType.Stinger -> station.stingers
                SampleType.DJ -> station.djSamples
            }
            if (playMode == PlayMode.Order) list
            else list.shuffled()
        }.filter { it.resolvePath() != null }

        // TODO: 控制最大连续数
        val maxContinuousTrack = 0
        val maxContinuousStinger = 0
        val maxContinuousDj = 0

        if (playMode == PlayMode.Shuffle) {
            val pool = allSamples.toMutableList()
            var lastType: SampleType? = null
            var runLen = 0

            while (pool.isNotEmpty()) {
                val candidates =
                    if (lastType == null) pool
                    else {
                        val limit = when (lastType) {
                            SampleType.Track -> maxContinuousTrack
                            SampleType.Stinger -> maxContinuousStinger
                            SampleType.DJ -> maxContinuousDj
                        }
                        if (limit in 1..runLen)
                            pool.filter { it.type != lastType }.ifEmpty { pool }
                        else pool
                    }
                val picked = candidates[Random.nextInt(candidates.size)]
                pool.remove(picked)

                if (picked.type != lastType) {
                    lastType = picked.type
                    runLen = 1
                } else {
                    runLen++
                }
                playlist.add(picked)
            }
        } else {
            playlist.addAll(allSamples)
        }
    }

    private fun nextSampleType(): SampleType? {
        if (usePatternMode) {
            // TODO
        }

        buildPlaylist()
        if (playlist.isEmpty()) return null
        return playlist[playlistIndex % playlist.size].type
    }

    override fun scheduleModeMarkers(sample: Sample, beginAt: Duration) {
        when (sample) {
            is TrackSample -> {
                when (nextSampleType()) {
                    SampleType.Stinger -> if (sample.stingerStart > 0) {
                        Scheduler.scheduleMarker(
                            tag = "Track.StingerStart",
                            sample = sample,
                            targetPos = sample.stingerStart,
                            beginAt = beginAt,
                        ) {
                            debugSnack("Track.StingerStart @ ${sample.stingerStart}")
                            Radio.playNext(SampleType.Track)
                        }
                    }

                    SampleType.DJ -> if (sample.djStart > 0) {
                        Scheduler.scheduleMarker(
                            tag = "Track.DJStart",
                            sample = sample,
                            targetPos = sample.djStart,
                            beginAt = beginAt,
                        ) {
                            debugSnack("Track.DJStart @ ${sample.djStart}")
                            Radio.playNext(SampleType.Track)
                        }
                    }

                    else -> {}
                }
            }

            is StingerSample -> {
                if (sample.startNextTrack > 0 && Radio.settings.crossFadeEnabled && nextSampleType() == SampleType.Track)
                    Scheduler.scheduleMarker(
                        tag = "Stinger.StartNextTrack",
                        sample = sample,
                        targetPos = sample.startNextTrack,
                        beginAt = beginAt,
                    ) {
                        debugSnack("Stinger.StartNextTrack @ ${sample.startNextTrack}")
                        Radio.playNext(SampleType.Track)
                    }
            }

            is DjSample -> {}
        }
    }

    override fun getResume(playbackState: SettingsStore.PlaybackState): PlayItem? {
        val name = playbackState.soundName
        val pos = playbackState.position
        val sampleType = when (playbackState.type) {
            "Track" -> SampleType.Track
            "Stinger" -> SampleType.Stinger
            "DJ" -> SampleType.DJ
            else -> return null
        }

        // 有名字, 找出来播放
        if (name != null) {
            val list = when (sampleType) {
                SampleType.Track -> station.tracks
                SampleType.Stinger -> station.stingers
                SampleType.DJ -> station.djSamples
            }
            list.find { it.soundName == name }
                ?.let {
                    return PlayItem(
                        sample = it,
                        beginAt = pos,
                    )
                }
        }

        // 构造播放列表后给第一首
        buildPlaylist()
        return PlayItem(sample = playlist.first())
    }

    override fun getNext(type: SampleType, step: Int, exclude: Set<Sample>): PlayItem? {
        if (Radio.settings.patternEnabled) {
            return when (Radio.settings.playMode) {
                PlayMode.Shuffle -> Radio.randomSample(type, exclude)
                PlayMode.Order -> {
                    val list = Radio.sampleList(type) ?: return null
                    val idx = Radio.lastPlayedSample
                        ?.takeIf { it.type == type }
                        ?.let { list.indexOf(it) }
                        ?: -1
                    if (idx >= 0) list[(idx + step) % list.size]
                    else list.first()
                }
            }?.let { PlayItem(sample = it) }
        }

        buildPlaylist()
        if (playlist.isEmpty()) return null
        if (playlistIndex >= playlist.size) playlistIndex = 0
        return PlayItem(sample = playlist[playlistIndex++])
    }
}
