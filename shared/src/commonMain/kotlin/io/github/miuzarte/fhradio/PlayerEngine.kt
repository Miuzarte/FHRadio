package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*
import kotlin.random.Random
import kotlin.time.Duration


class PlayerEngine(
    station: RadioStation,
    val playMode: PlayMode, // shuffle / order
    val crossLists: Set<SampleType>,
    val patternEnabled: Boolean,
    val patternNodes: List<PatternNode>,
) : RadioModeEngineV2(station) {

    // 提供 current: 返回 current 的下一首, 到末尾时自动重建列表
    // 不提供 current: 返回自身维护的计数器的下一首
    override fun next(current: PlaySection?): PlaySection {
        // 获取当前播放的 Sample 的 index
        val currentIndex = current.toSample()
            ?.let { prebuiltSampleList.indexOf(it) }

        val nextSample = prebuiltPlayListNext(
            currentIndex?.takeIf { it > 0 }
        )

        return nextSample.toPlaySection()
    }

    override fun resume(playbackState: PlaybackState?): PlaySection? {
        val name = playbackState?.soundName
        val pos = playbackState?.position
        // 不需要判断 Sample 类型, prebuiltSampleList: List<Sample>

        // 有名字, 找出来返回
        name?.let {
            prebuiltSampleList.withIndex()
                .find { it.value.soundName == name }
                ?.let {
                    prebuiltSampleListIndex = it.index
                    return pos
                        ?.let { pos -> it.value.toPlaySection(pos) }
                        ?: it.value.toPlaySection()
                }
        }

        // 没给/没找到, 给第一首, 从头开始播放
        return prebuiltSampleList.firstOrNull()?.toPlaySection()
    }

    override fun getPlayList() =
        prebuiltSampleList.map { it.toPlaySection() } to prebuiltSampleListIndex

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
    private var prebuiltSampleListIndex = 0

    private fun rebuildPlayList() {
        prebuiltSampleListIndex = 0
        prebuiltSampleList = buildPlayList()
    }

    private fun prebuiltPlayListNext(overrideIndex: Int? = null): Sample {
        overrideIndex?.let { prebuiltSampleListIndex = it }
        return prebuiltSampleList[prebuiltSampleListIndex]
            .also {
                prebuiltSampleListIndex++
                if (prebuiltSampleListIndex == prebuiltSampleList.size)
                    rebuildPlayList() // 消费完毕后重新构建列表
            }
    }

    // 构建播放列表
    // TODO: 实现 LoopPattern
    private fun buildPlayList(
        // TODO: 控制最大连续数, 0 禁用/无限制
        // 放到类构造变量中
        maxContinuousTrack: Int = 0,
        maxContinuousStinger: Int = 0,
        maxContinuousDj: Int = 0,
    ): List<Sample> {
        require(crossLists.isNotEmpty()) { "crossLists can't be empty" }

        // 根据 crossLists 获取待选
        val candidateSamples: Map<SampleType, List<Sample>> =
            crossLists
                .associateWith { type ->
                    station.samplesFor(type)
                        .filter { station.resolvePath(it) != null }
                }
                .filterValues { it.isNotEmpty() }

        return when (playMode) {
            PlayMode.Shuffle -> {
                if (candidateSamples.isEmpty()) return emptyList()

                val initialCapacity = candidateSamples.values.sumOf { it.size }
                val playList = ArrayList<Sample>(initialCapacity)
                // 打乱并变成可变的 pool, 之后按条件一条一条抽
                val samplePools = candidateSamples.mapValues {
                    ArrayDeque(it.value.shuffled())
                }

                // 遵循 crossLists, 不强制以 Track 开始
                var lastType: SampleType? = null
                var lastTypeLen = 0

                while (playList.size < initialCapacity) {
                    val limit = when (lastType) {
                        SampleType.Track -> maxContinuousTrack
                        SampleType.Stinger -> maxContinuousStinger
                        SampleType.DJ -> maxContinuousDj
                        null -> 0
                    }

                    // 确定可用的池, 考虑连续限制
                    val availablePools =
                        if (limit in 1..lastTypeLen) samplePools.filterKeys { it != lastType }
                        else samplePools

                    val chosenPool = selectPoolByWeight(availablePools)
                    // 忽略连续限制, 从所有非空池中加权选择
                        ?: selectPoolByWeight(samplePools)
                        ?: break
                    // 双端队列可以使用 removeFirst
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
                // Track, Stinger, DJ
                SampleType.entries.filter { it in crossLists }
                    .flatMap { candidateSamples[it]!! }
            }
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
