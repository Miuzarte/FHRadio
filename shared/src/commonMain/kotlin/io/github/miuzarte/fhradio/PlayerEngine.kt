package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*
import kotlin.random.Random
import kotlin.time.Duration


class PlayerEngine(
    station: RadioStation,
    val playMode: PlayMode, // shuffle / order
    val crossLists: Set<SampleType>,
    val maxContinuousTrack: Int,
    val maxContinuousStinger: Int,
    val maxContinuousDj: Int,
    val patternEnabled: Boolean,
    val patternNodes: List<PatternNode>,
    val excludedTrackSuffixes: Set<String>,
): RadioModeEngineV2(station) {

    // 提供 current: 返回 current 的下一首, 到末尾时自动重建列表
    // 不提供 current: 返回自身维护的计数器的下一首
    override fun next(current: PlaySection?): PlaySection {
        current.toSample()
            ?.let { prebuiltSampleList.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.let { prebuiltSampleListIndex = it }

        return prebuiltPlayListNext().toPlaySection()
    }

    override fun resume(playbackState: PlaybackState?): PlaySection? {
        val name = playbackState?.soundName
        val pos = playbackState?.position

        name?.let {
            prebuiltSampleList.withIndex()
                .find { it.value.soundName == name }
                ?.let { (i, sample) ->
                    prebuiltSampleListIndex = i
                    return pos
                        ?.let { pos -> sample.toPlaySection(pos) }
                        ?: sample.toPlaySection()
                }
        }

        prebuiltSampleListIndex = 0
        return prebuiltSampleList.firstOrNull()?.toPlaySection()
    }

    override fun getPlayList() =
        prebuiltSampleList.map { it.toPlaySection() } to prebuiltSampleListIndex

    override fun onSectionStarted(section: PlaySection) {
        section.toSample()?.let { sample ->
            prebuiltSampleList.indexOf(sample).takeIf { it >= 0 }?.let {
                prebuiltSampleListIndex = it
            }
        }
    }

    // PlayerEngine 中 PlaySection 的三个字段互斥
    private fun PlaySection?.toSample(): Sample? =
        this?.let { it.track ?: it.stinger ?: it.dj }
            ?.sample

    // PlayerEngine 中 PlaySection 的三个字段互斥
    private fun Sample.toPlaySection(beginAt: Duration = Duration.ZERO): PlaySection =
        when (this) {
            is TrackSample -> PlaySection(track = PlayItem.Track(this, beginAt))
            is StingerSample -> PlaySection(stinger = PlayItem.Stinger(this, beginAt))
            is DjSample -> PlaySection(dj = PlayItem.Dj(this, beginAt))
        }

    // 播放列表
    private var prebuiltSampleList: List<Sample> = buildPlayList()
    private var prebuiltSampleListIndex: Int? = null

    private fun rebuildPlayList() {
        prebuiltSampleListIndex = 0
        prebuiltSampleList = buildPlayList()
    }

    private fun prebuiltPlayListNext(): Sample {
        val idx = ((prebuiltSampleListIndex ?: -1) + 1) % prebuiltSampleList.size
        if (idx == 0) rebuildPlayList()
        prebuiltSampleListIndex = idx
        return prebuiltSampleList[idx]
    }

    // 构建播放列表
    private fun buildPlayList(): List<Sample> {
        if (patternEnabled && patternNodes.isNotEmpty()) {
            val patternList = buildPatternPlaylist()
            if (patternList.isNotEmpty()) return patternList
        }
        return buildCrossListPlaylist()
    }

    private fun buildCrossListPlaylist(): List<Sample> {
        require(crossLists.isNotEmpty()) { "crossLists can't be empty" }

        val candidateSamples: Map<SampleType, List<Sample>> =
            crossLists
                .associateWith { type ->
                    station.samplesFor(type)
                        .filter { station.resolvePath(it) != null }
                        .filterNot {
                            type == SampleType.Track &&
                                    excludedTrackSuffixes.any { suffix ->
                                        it.soundName.endsWith(suffix)
                                    }
                        }
                }
                .filterValues { it.isNotEmpty() }

        return when (playMode) {
            PlayMode.Shuffle -> {
                if (candidateSamples.isEmpty()) return emptyList()

                val initialCapacity = candidateSamples.values.sumOf { it.size }
                val playList = ArrayList<Sample>(initialCapacity)
                val samplePools = candidateSamples.mapValues {
                    ArrayDeque(it.value.shuffled())
                }

                var lastType: SampleType? = null
                var lastTypeLen = 0

                while (playList.size < initialCapacity) {
                    val limit = when (lastType) {
                        SampleType.Track -> maxContinuousTrack
                        SampleType.Stinger -> maxContinuousStinger
                        SampleType.DJ -> maxContinuousDj
                        null -> 0
                    }

                    val availablePools =
                        if (limit in 1..lastTypeLen) samplePools.filterKeys { it != lastType }
                        else samplePools

                    val chosenPool = selectPoolByWeight(availablePools)
                        ?: selectPoolByWeight(samplePools)
                        ?: break
                    val picked = chosenPool.removeFirst()

                    if (picked.type != lastType) {
                        lastType = picked.type
                        lastTypeLen = 1
                    } else {
                        lastTypeLen++
                    }
                    playList.add(picked)
                }

                playList
            }

            PlayMode.Order -> {
                SampleType.entries.filter { it in crossLists }
                    .flatMap { candidateSamples[it]!! }
            }
        }
    }

    private fun selectPoolByWeight(pools: Map<SampleType, ArrayDeque<Sample>>): ArrayDeque<Sample>? {
        val total = pools.values.sumOf { it.size }
        if (total == 0) return null
        var index = Random.nextInt(total)
        for (pool in pools.values) {
            if (index < pool.size) return pool
            index -= pool.size
        }
        error("unreachable")
    }

    private fun buildPatternPlaylist(): List<Sample> {
        fun Int.roll(until: Int = 100): Boolean =
            Random.nextInt(until) < this

        val typeLists = patternNodes.map { it.type }.toSet()
            .associateWith { type ->
                station.samplesFor(type)
                    .filter { station.resolvePath(it) != null }
                    .filterNot {
                        type == SampleType.Track &&
                                excludedTrackSuffixes.any { suffix ->
                                    it.soundName.endsWith(suffix)
                                }
                    }
            }
            .filterValues { it.isNotEmpty() }

        if (typeLists.isEmpty()) return emptyList()

        val maxSize = minOf(
            typeLists.values.sumOf { it.size * 3 },
            384,
        )

        val playlist = mutableListOf<Sample>()
        val typeIndices = mutableMapOf<SampleType, Int>()

        while (playlist.size < maxSize) {
            var roundAdded = false
            for (node in patternNodes) {
                if (playlist.size >= maxSize) break
                if (!node.probability.roll()) continue

                val list = typeLists[node.type] ?: continue
                val idx = typeIndices[node.type]
                    ?.let { (it + node.step).mod(list.size) }
                    ?: 0
                playlist.add(list[idx])
                typeIndices[node.type] = idx
                roundAdded = true
            }
            if (!roundAdded) break
        }

        return playlist
    }
}
