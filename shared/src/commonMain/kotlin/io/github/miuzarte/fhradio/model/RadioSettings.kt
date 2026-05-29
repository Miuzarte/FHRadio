package io.github.miuzarte.fhradio.model

import io.github.miuzarte.fhradio.audioDuckingDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class RadioMode {
    Random,
    Seed,
    Player,
}

@Serializable
data class RadioSettings(
    // Radio
    val radioMode: RadioMode = RadioMode.Seed,

    // RandomMode
    val stingerProbability: Int = 50,
    val djProbability: Int = 15,

    val djGameEventsJson: String = "[]",

    // SeedMode
    val seedString: String = "FHRadio",

    // PlayerMode
    val playMode: PlayMode = PlayMode.Shuffle,

    val crossListsJson: String = """["${SampleType.Track}"]""",

    val maxContinuousTrack: Int = 0,
    val maxContinuousStinger: Int = 1,
    val maxContinuousDj: Int = 1,

    val patternEnabled: Boolean = false,
    val patternJson: String = """[{},{"step":2},{"step":3},{"type":"Stinger"},{"step":4},{}]""",

    val crossFadeEnabled: Boolean = true,

    val excludedTrackSuffixesJson: String = """["_ID","_FI","_LI"]""",

    // Application
    val volume: Int = 100,
    val audioDucking: Boolean = audioDuckingDefault,
    val autoResume: Boolean = false,
    val tracksTopAppBarKeepProgressBar: Boolean = false,

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
    val djGameEvents: Set<String> get() = parsedDjGameEvents.ifEmpty { defaultDjGameEvents }

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
        val defaultDjGameEvents = setOf(
            // 改装市场 / 二手车闲聊
            "DJAftermarket1",
            // NPC 角色介绍 (海莲娜、幽人、浜田、中本)
            "DJAmbassador1", "DJAmbassador2", "DJAmbassador3", "DJAmbassador4",
            // ANNA 自动驾驶闲扯
            "DJAutoDrive1",
            // 车库装潢 / 舞会
            "DJGarage1", "DJGarage2",
            // 探索日本 / 盖章 / 新生活
            "DJCampaignFestival6",
            "DJCampaignFestivalNew1", "DJCampaignFestivalNew2", "DJCampaignFestivalNew3",
            "DJCampaignFestivalNew4", "DJCampaignFestivalNew5", "DJCampaignFestivalNew6",
        )
        private val json = Json { ignoreUnknownKeys = true }
    }
}
