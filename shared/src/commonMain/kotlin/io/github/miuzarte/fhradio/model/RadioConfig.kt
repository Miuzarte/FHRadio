package io.github.miuzarte.fhradio.model

import kotlinx.serialization.Serializable

@Serializable
data class RadioConfig(
    val version: Int,
    val timeBetweenMidTrackDJLinesSeconds: Int,
    val recentlyPlayedMaxSize: Int,
    val stations: List<RadioStation>,
)

@Serializable
data class RadioStation(
    val name: String,
    val number: Int,
    val djCharId: Int,
    val tracks: List<TrackSample> = emptyList(),
    val djSamples: List<DjSample> = emptyList(),
    val stingers: List<StingerSample> = emptyList(),
    val freeRoamPlayList: PlayList = PlayList(type = PlayListType.FreeRoam),
    val eventPlayList: PlayList = PlayList(type = PlayListType.Event),
    val shortStingerPlayList: PlayList = PlayList(type = PlayListType.ShortStinger),
) {
    fun samplesForPlayList(type: SampleType): List<Sample> =
        when (type) {
            SampleType.Track -> tracks
            SampleType.Stinger -> stingers
            SampleType.DJ -> djSamples
        }

    fun samplesForPlayList(type: PlayListType): List<Sample> =
        when (type) {
            PlayListType.FreeRoam ->
                freeRoamPlayList.entries.mapNotNull { entry -> tracks.find { it.soundName == entry.name } }

            PlayListType.Event ->
                eventPlayList.entries.mapNotNull { entry -> tracks.find { it.soundName == entry.name } }

            PlayListType.ShortStinger ->
                shortStingerPlayList.entries.mapNotNull { entry -> stingers.find { it.soundName == entry.name } }
        }

    val allSamples: List<Sample>
        get() = tracks + djSamples + stingers

}
