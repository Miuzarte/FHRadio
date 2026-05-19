package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayMode

enum class RadioMode { Random, Seed, Player }

data class RadioSettings(
    val radioMode: RadioMode = RadioMode.Random,
    val playMode: PlayMode = PlayMode.Shuffle,
    // val playListType: PlayListType = PlayListType.Track,

    val stingerChance: Int = 10,
    val djChance: Int = 1,

    val sourcesJson: String = "[]",
    val autoResume: Boolean = false,
    val lastStationXmlPath: String? = null,
    val lastStationName: String? = null,
    val patternEnabled: Boolean = false,
    val patternJson: String = "[]",
    val crossListsJson: String = """["Track"]""",
    val crossFadeEnabled: Boolean = true,
)
