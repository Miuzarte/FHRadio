package io.github.miuzarte.fhradio.model

import kotlinx.serialization.json.Json

enum class RadioMode {
    Random,
    Seed,
    Player,
}

data class RadioSettings(
    // radio
    val radioMode: RadioMode = RadioMode.Random,
    val playMode: PlayMode = PlayMode.Shuffle,

    val stingerProbability: Int = 10,
    val djProbability: Int = 1,

    val crossListsJson: String = """["Track"]""",

    val patternEnabled: Boolean = false,
    val patternJson: String = """[]""",

    val crossFadeEnabled: Boolean = true,

    // application
    val autoResume: Boolean = false,

    // internal
    val lastStationXmlPath: String? = null,
    val lastStationName: String? = null
) {

    // crossLists
    private val parsedCrossLists by lazy {
        runCatching {
            json.decodeFromString<List<SampleType>>(crossListsJson)
        }
            .getOrDefault(emptyList())
            .toSet()
    }
    val crossLists: Set<SampleType> get() = parsedCrossLists

    fun withCrossLists(newList: Set<SampleType>): RadioSettings {
        val clJson = json.encodeToString(newList.toList())
        return copy(crossListsJson = clJson)
    }

    // patternNodes
    private val parsedPatternNodes by lazy {
        runCatching {
            json.decodeFromString<List<PatternNode>>(patternJson)
        }.getOrDefault(emptyList())
    }
    val patternNodes: List<PatternNode> get() = parsedPatternNodes

    fun withPatternNodes(newList: List<PatternNode>): RadioSettings {
        val newJson = json.encodeToString(newList)
        return copy(patternJson = newJson)
    }

    companion object {
        val defaults = RadioSettings()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
