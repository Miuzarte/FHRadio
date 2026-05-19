package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.SampleType
import kotlinx.serialization.Serializable

@Serializable
data class PatternNode(
    val type: SampleType = SampleType.Track,
    val step: Int = 1,
    val probability: Int = 100,
)
