package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import kotlin.time.Duration

abstract class RadioModeEngine {
    abstract fun scheduleModeMarkers(sample: Sample, beginAt: Duration = Duration.ZERO)
    abstract fun resume()
    abstract fun advance()
    abstract fun nextSample(type: SampleType, step: Int = 1, exclude: Set<Sample> = emptySet()): Sample?
    open fun onSamplePlayed(sample: Sample) {}
    open fun resetPatternState() {}
}
