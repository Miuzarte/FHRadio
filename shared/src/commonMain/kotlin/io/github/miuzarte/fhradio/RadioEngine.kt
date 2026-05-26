package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*
import kotlin.time.Duration

sealed class PlayItem {
    abstract val sample: Sample
    abstract val beginAt: Duration

    data class Track(override val sample: TrackSample, override val beginAt: Duration = Duration.ZERO) : PlayItem()
    data class Stinger(override val sample: StingerSample, override val beginAt: Duration = Duration.ZERO) : PlayItem()
    data class Dj(override val sample: DjSample, override val beginAt: Duration = Duration.ZERO) : PlayItem()
}

data class PlaySection(
    val track: PlayItem.Track? = null,
    val stinger: PlayItem.Stinger? = null,
    val dj: PlayItem.Dj? = null,
    val solo: Boolean = false,
) {
    init {
        require(track != null || stinger != null || dj != null) {
            "At least one of track, stinger, or dj must be non-null"
        }

        // 让使用方按需判断
        // if (isStingerAndDjMutuallyExclusive()) {
        //     "Stinger and DJ coexist"
        // }
    }

    val isStingerAndDjMutuallyExclusive: Boolean
        get() = stinger == null || dj == null

    val isTrackOnly: Boolean
        get() = track != null && stinger == null && dj == null

    val isTrackAndStinger: Boolean
        get() = track != null && stinger != null

    val isTrackAndDj: Boolean
        get() = track != null && dj != null
}

abstract class RadioModeEngineV2(
    val station: RadioStation, // not nullable
) {
    // 提供当前播放的, 返回下一个要播放的
    // 在 RandomEngine 中, 如果 current 为 null, 可以返回随机曲目与随机切入点用于初始播放
    abstract fun next(current: PlaySection?): PlaySection

    // 提供持久化的信息, Engine 决定续播逻辑
    abstract fun resume(playbackState: PlaybackState?): PlaySection?

    // Radio 有整个 PlaySection, 知道接下来播放什么, 同时负责派发
    // abstract fun scheduleFor(section: PlaySection): Boolean

    // 返回列表和当前的索引
    // 使用双端队列时可以转为列表并固定索引为 0
    open fun getPlayList(): Pair<List<PlaySection>, Int?>? = null

    // 用于向 Engine 同步播放状态
    open fun onSectionStarted(section: PlaySection) {}
}

abstract class RadioModeEngine(
    val station: RadioStation, // not nullable
) {
    abstract fun scheduleModeMarkers(
        sample: Sample,
        beginAt: Duration = Duration.ZERO,
    )

    // 用于单个电台的继续播放
    abstract fun getResume(
        playbackState: PlaybackState,
    ): PlayItem?

    abstract fun getNext(
        type: SampleType,
        step: Int = 1,
        exclude: Set<Sample> = emptySet(),
    ): PlayItem?

    // 重置 engine 状态
    // 例如重新构建播放列表
    abstract fun reset()

    open fun getPlaylist(): Pair<List<Sample>, Int>? = null

    // 提供当前播放的, 返回下一个要播放的
    // RandomEngine 中, 如果 current 为 null, 可以返回随机曲目与随机切入点用于初始播放
    open fun next(current: Sample?): PlayItem? = null

    // 提供 PlayItem, Engine 负责派发 Marker
    open fun scheduleFor(playItem: PlayItem): Boolean = false
}
