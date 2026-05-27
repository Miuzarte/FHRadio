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
    val djProbability: Int = 0,

    val djGameEventsJson: String = "[]",

    val crossListsJson: String = """["${SampleType.Track}"]""",

    val maxContinuousTrack: Int = 0,
    val maxContinuousStinger: Int = 1,
    val maxContinuousDj: Int = 1,

    val patternEnabled: Boolean = false,
    val patternJson: String = """[{},{"step":2},{"step":3},{"type":"Stinger"},{"step":4},{}]""",

    val crossFadeEnabled: Boolean = true,

    val excludedTrackSuffixesJson: String = """["_ID","_FI","_LI"]""",

    // application
    val volume: Int = 100,
    val autoResume: Boolean = false,

    // internal
    val lastStationXmlPath: String? = null,
    val lastStationName: String? = null,
) {

    // djGameEvents
    private val parsedDjGameEvents by lazy {
        runCatching {
            json.decodeFromString<List<String>>(djGameEventsJson)
        }.getOrDefault(emptyList()).toSet()
    }
    val djGameEvents: Set<String> get() = parsedDjGameEvents

    fun withDjGameEvents(newSet: Set<String>): RadioSettings {
        return copy(djGameEventsJson = json.encodeToString(newSet.toList()))
    }

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

    // excludedTrackSuffixes
    private val parsedExcludedTrackSuffixes by lazy {
        runCatching {
            json.decodeFromString<List<String>>(excludedTrackSuffixesJson)
        }.getOrDefault(emptyList()).toSet()
    }
    val excludedTrackSuffixes: Set<String> get() = parsedExcludedTrackSuffixes

    fun withExcludedTrackSuffixes(newSet: Set<String>): RadioSettings {
        val newJson = json.encodeToString(newSet.toList())
        return copy(excludedTrackSuffixesJson = newJson)
    }

    companion object {
        val defaults = RadioSettings()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
