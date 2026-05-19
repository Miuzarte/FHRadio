package io.github.miuzarte.fhradio

import kotlinx.serialization.Serializable

@Serializable
data class RadioSource(
    val name: String = "新电台源",
    val xmlFilePath: String,
    val audioFolderPath: String,
    val stationOrder: List<Int> = emptyList(),
    val audioExtension: String = "wav",
)
