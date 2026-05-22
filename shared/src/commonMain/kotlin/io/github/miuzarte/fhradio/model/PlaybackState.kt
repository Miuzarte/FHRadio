package io.github.miuzarte.fhradio.model

import kotlin.time.Duration

data class PlaybackState(
    val soundName: String? = null,
    val position: Duration = Duration.ZERO,
    val sampleType: SampleType? = null,
)
