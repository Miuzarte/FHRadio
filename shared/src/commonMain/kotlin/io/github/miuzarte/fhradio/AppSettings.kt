package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.SampleType
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json

object AppSettings {
    var radioSettings by mutableStateOf(SettingsStore.loadSettings())
    var radioMode by mutableStateOf(radioSettings.radioMode)
    var playMode by mutableStateOf(radioSettings.playMode)

    var sources: List<RadioSource> by mutableStateOf(SettingsStore.loadSources())
        private set
    var sourceStations: Map<String, List<RadioStation>> by mutableStateOf(emptyMap())
        private set

    private val json = Json { ignoreUnknownKeys = true }

    var crossLists: List<SampleType> by mutableStateOf(loadCrossLists())
        private set

    private fun loadCrossLists(): List<SampleType> = runCatching {
        json.decodeFromString<List<SampleType>>(radioSettings.crossListsJson)
    }.getOrDefault(listOf(SampleType.Track))

    fun saveCrossLists(list: List<SampleType>) {
        crossLists = list
        val s = radioSettings.copy(
            crossListsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(SampleType.serializer()), list
            )
        )
        saveSettings(s)
        Radio.reschedule()
    }

    private var cachedPatternJson: String? = null
    private var cachedPatternNodes: List<PatternNode> = emptyList()

    fun loadPatternNodes(): List<PatternNode> {
        val currentJson = radioSettings.patternJson
        if (currentJson != cachedPatternJson) {
            cachedPatternJson = currentJson
            cachedPatternNodes = runCatching {
                json.decodeFromString<List<PatternNode>>(currentJson)
            }.getOrDefault(emptyList())
        }
        return cachedPatternNodes
    }

    fun savePatternNodes(nodes: List<PatternNode>) {
        val s = radioSettings.copy(
            patternJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(PatternNode.serializer()),
                nodes
            ),
        )
        saveSettings(s)
        cachedPatternJson = s.patternJson
        cachedPatternNodes = nodes
        Radio.resetPatternState()
        Radio.reschedule()
    }

    fun saveSettings(settings: RadioSettings) {
        radioSettings = settings
        radioMode = settings.radioMode
        playMode = settings.playMode
        SettingsStore.saveSettings(settings)
    }

    fun updateSettings(settings: RadioSettings) {
        radioSettings = settings
        radioMode = settings.radioMode
        playMode = settings.playMode
    }

    fun addSource(source: RadioSource, stations: List<RadioStation>) {
        sources = sources + source
        sourceStations = sourceStations + (source.xmlFilePath to stations)
        SettingsStore.saveSources(sources)
    }

    fun updateSource(source: RadioSource) {
        sources = sources.map { if (it.xmlFilePath == source.xmlFilePath) source else it }
        SettingsStore.saveSources(sources)
    }

    fun saveSources(newSources: List<RadioSource>) {
        sources = newSources
        SettingsStore.saveSources(sources)
    }

    fun removeSource(xmlFilePath: String) {
        val removedStations = sourceStations[xmlFilePath] ?: emptyList()
        sources = sources.filter { it.xmlFilePath != xmlFilePath }
        sourceStations = sourceStations - xmlFilePath
        SettingsStore.saveSources(sources)

        if (Radio.selectedStation in removedStations) {
            Radio.closeStation()
        }
    }

    fun findSourcePath(station: RadioStation): String? {
        for ((xmlPath, stations) in sourceStations) {
            if (stations.any { it === station }) {
                return sources.find { it.xmlFilePath == xmlPath }?.audioFolderPath
            }
        }
        return null
    }

    fun findSourceExtension(station: RadioStation): String {
        for ((xmlPath, stations) in sourceStations) {
            if (stations.any { it === station }) {
                return sources.find { it.xmlFilePath == xmlPath }?.audioExtension ?: "wav"
            }
        }
        return "wav"
    }

    /** 从已持久化的 source 列表恢复电台数据 */
    fun restoreFromPaths(): Job? {
        if (sources.isEmpty()) return null
        return CoroutineScope(Dispatchers.Default).launch {
            val newStations = mutableMapOf<String, List<RadioStation>>()
            for (source in sources) {
                val xml = readFileTextOrNull(source.xmlFilePath) ?: continue
                val result = RadioXmlParser.parse(xml)
                AudioScanner().verifyOnly(result, source.audioFolderPath)
                newStations[source.xmlFilePath] = result.stations
            }
            withContext(Dispatchers.Main) {
                sourceStations = newStations
            }
        }
    }

    fun saveLastStation(station: RadioStation) {
        val xmlPath = sources.find { src ->
            sourceStations[src.xmlFilePath]?.any { it === station } == true
        }?.xmlFilePath ?: return
        saveSettings(
            radioSettings.copy(
                lastStationXmlPath = xmlPath,
                lastStationName = station.name,
            )
        )
    }
}
