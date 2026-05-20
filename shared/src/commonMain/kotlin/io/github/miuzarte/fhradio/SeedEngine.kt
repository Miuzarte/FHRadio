package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import kotlin.time.Duration

class SeedEngine(station: RadioStation) : RadioModeEngine(station) {
    override fun scheduleModeMarkers(sample: Sample, beginAt: Duration) {
        TODO("种子控制模式未实现")
    }

    override fun getResume() {
        TODO("种子控制模式未实现")
    }

    override fun advance() {
        TODO("种子控制模式未实现")
    }

    override fun getNext(type: SampleType, step: Int, exclude: Set<Sample>): Sample? {
        TODO("种子控制模式未实现")
    }

    override fun reset() {
        TODO("种子控制模式未实现")
    }
}
