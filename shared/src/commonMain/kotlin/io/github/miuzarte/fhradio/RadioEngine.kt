package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import kotlin.time.Duration

data class PlayItem(
    val sample: Sample,
    val beginAt: Duration = Duration.ZERO,
)

abstract class RadioModeEngine(
    val station: RadioStation, // not nullable
) {
    abstract fun scheduleModeMarkers(
        sample: Sample,
        beginAt: Duration = Duration.ZERO,
    )

    // 用于单个电台的继续播放
    abstract fun getResume(
        playbackState: SettingsStore.PlaybackState,
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
}

