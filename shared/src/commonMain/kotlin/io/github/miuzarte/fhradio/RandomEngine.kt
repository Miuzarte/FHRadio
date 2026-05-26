package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*
import kotlin.random.Random
import kotlin.time.Duration

class RandomEngine(
    station: RadioStation,
    val stingerProbability: Int,
    val djProbability: Int,
    val djGameEvents: Set<String>,
    val excludedTrackSuffixes: Set<String>,
) : RadioModeEngineV2(station) {

    // 提供 current 时返回值保证不会重复
    override fun next(current: PlaySection?): PlaySection {
        current?.let { updatePlayed(it) }
        return playDequeNext()
    }

    // 找到对应 Sample 时返回 Sample 与 切入点
    // 未找到时返回随机 TrackSample 与 随机切入点
    override fun resume(playbackState: PlaybackState?): PlaySection {
        fun returnWithUpdate(ps: PlaySection): PlaySection {
            return ps.also { updatePlayed(it) }
        }

        val name = playbackState?.soundName
        val pos = playbackState?.position
        val sampleType = playbackState?.sampleType

        // 默认 没给/没找到, 随便给
        var playSection = playDeque.removeFirst()
            .also {
                // 补充队列
                playDeque.addLast(rollPlaySection())
                // 不放入 played 列表, 不知道是否真的使用
                // updatePlayed(it)
            }

        name?.let {
            val samples = sampleType
                ?.let { station.samplesFor(it) }
                ?: station.allSamples

            samples.find { it.soundName == name }
                ?.let {
                    // 找到了, 覆盖 playSection 之后返回
                    when (it) {
                        is TrackSample -> {
                            playSection = playSection.copy(
                                track = PlayItem.Track(
                                    sample = it,
                                    beginAt = pos ?: Duration.ZERO,
                                )
                            )
                        }

                        is StingerSample -> {
                            playSection = playSection.copy(
                                stinger = PlayItem.Stinger(
                                    sample = it,
                                    beginAt = pos ?: Duration.ZERO,
                                )
                            )
                        }

                        is DjSample -> {
                            playSection = playSection.copy(
                                dj = PlayItem.Dj(
                                    sample = it,
                                    beginAt = pos ?: Duration.ZERO,
                                )
                            )
                        }
                    }

                    return returnWithUpdate(playSection)
                }
        }

        // 带上随机切入点
        playSection.track?.let {
            playSection = playSection.copy(
                track = it.copy(beginAt = it.sample.randomBeginAt())
            )
            // 从 Track 开始播, 其他的不中途切入
            return returnWithUpdate(playSection)
        }

        when {
            playSection.stinger != null -> {
                playSection.stinger.let {
                    playSection = playSection.copy(
                        stinger = it.copy(beginAt = it.sample.randomBeginAt())
                    )
                }
            }

            playSection.dj != null -> {
                playSection.dj.let {
                    playSection = playSection.copy(
                        dj = it.copy(beginAt = it.sample.randomBeginAt())
                    )
                }
            }
        }

        return returnWithUpdate(playSection)
    }

    override fun getPlayList() =
        playDeque.toList() to null

    // 随机数 helper
    private fun Int.roll(until: Int = 100): Boolean =
        Random.nextInt(until) < this

    private fun <T> Int.roll(until: Int = 100, block: () -> T): T? =
        this.roll(until).run(block)

    private fun <T> Boolean.run(block: () -> T): T? =
        if (this) block()
        else null

    // 播放队列
    private val playDeque = ArrayDeque<PlaySection>(PLAY_DEQUE_SIZE)

    private fun playDequeNext(): PlaySection {
        return playDeque.removeFirst()
            .also {
                updatePlayed(it) // 放入 played 列表
                playDeque.addLast(rollPlaySection()) // 补充队列
            }
    }

    // 按后缀排除的曲目, 始终附加到 randomTrack(exclude) 中
    private val suffixExcludedTracks: Set<TrackSample> by lazy {
        station.tracks.filterTo(mutableSetOf()) { t ->
            excludedTrackSuffixes.any { t.soundName.endsWith(it) }
        }
    }

    // DJ 白名单, 非空时只播指定 gameEvent 的 DJ
    private val djNotInWhiteList: Set<DjSample> by lazy {
        station.djSamples.filterTo(mutableSetOf()) {
            djGameEvents.isNotEmpty() && it.gameEvent !in djGameEvents
        }
    }

    private fun rollPlaySection(): PlaySection {
        // 一定会有 Track, 即不会连着播 Stinger / DJ
        val track = station.randomTrack(exclude = playedTrackDeque.toSet() + suffixExcludedTracks)
        var stinger = stingerProbability.roll { station.randomStinger(exclude = playedStingerDeque.toSet()) }
        var dj = djProbability.roll { station.randomDj(exclude = playedDjDeque.toSet() + djNotInWhiteList) }

        if (stinger != null && dj != null) {
            // 都 roll 到了, 随机去掉一个
            Random.nextBoolean()
                .run { stinger = null }
                ?: run { dj = null }
        }

        return PlaySection(
            track = track?.let { PlayItem.Track(it) },
            stinger = stinger?.let { PlayItem.Stinger(it) },
            dj = dj?.let { PlayItem.Dj(it) }
        ).also {
            require(it.isStingerAndDjMutuallyExclusive) { "FIXME: Stinger and DJ coexist" }
        }
    }

    // 用于避免最近一段时间重复播放相同曲目
    private val playedTrackDeque = ArrayDeque<TrackSample>(PLAYED_DEQUE_SIZE)
    private val playedStingerDeque = ArrayDeque<StingerSample>(PLAYED_DEQUE_SIZE)
    private val playedDjDeque = ArrayDeque<DjSample>(PLAYED_DEQUE_SIZE)

    private fun updatePlayed(playSection: PlaySection) {
        playSection.track.let { track ->
            playedTrackDeque.removeFirstOrNull()
            track?.let { playedTrackDeque.addLast(it.sample) }
        }
        playSection.stinger.let { stinger ->
            playedStingerDeque.removeFirstOrNull()
            stinger?.let { playedStingerDeque.addLast(it.sample) }
        }
        playSection.dj.let { dj ->
            playedDjDeque.removeFirstOrNull()
            dj?.let { playedDjDeque.addLast(it.sample) }
        }
    }

    init {
        // 初始化时填充队列, 用于预览播放列表
        repeat(PLAY_DEQUE_SIZE) {
            rollPlaySection()
                .also { updatePlayed(it) }
                .let { playDeque.addLast(it) }
        }
    }

    companion object {
        const val PLAY_DEQUE_SIZE = 4

        // 用于排除去重最近一段时间的 Sample,
        // 不能太大, 如 Stinger 通常只有 10 条
        const val PLAYED_DEQUE_SIZE = 5
    }
}
