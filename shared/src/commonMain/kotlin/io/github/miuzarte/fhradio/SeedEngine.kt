package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlaybackState
import io.github.miuzarte.fhradio.model.RadioStation

class SeedEngine(
    station: RadioStation,
) : RadioModeEngineV2(station) {
    override fun next(current: PlaySection?): PlaySection {
        TODO(TODO_MSG)
    }

    override fun resume(playbackState: PlaybackState?): PlaySection? {
        TODO(TODO_MSG)
    }

    override fun getPlayList(): Pair<List<PlaySection>, Int> =
        TODO(TODO_MSG)

    companion object {
        const val TODO_MSG = "种子控制模式未实现"
    }
}
