package io.github.miuzarte.fhradio.model

import kotlinx.serialization.Serializable

@Serializable
data class RadioSource(
    val name: String = "新电台源",
    val xmlFilePath: String,
    val audioFolderPath: String,
    val stationOrder: List<Int> = emptyList(),
    val audioExtension: String = "wav",
    val hiddenStationNames: Set<String> = setOf("Streamer Mode"),
)