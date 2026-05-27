package io.github.miuzarte.fhradio.model

import kotlinx.serialization.Serializable

@Serializable
data class PlayList(
    val type: PlayListType,
    val entries: List<PlayListEntry> = emptyList(),
)

@Serializable
data class PlayListEntry(
    val name: String,
)

@Serializable
enum class PlayListType: SampleSource {
    FreeRoam,
    Event,
    ShortStinger,

}

@Serializable
enum class PlayMode {
    Shuffle,
    Order,

}
