package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.PlaybackState
import io.github.miuzarte.fhradio.model.RadioSettings
import io.github.miuzarte.fhradio.model.RadioSource
import io.github.miuzarte.fhradio.model.RadioStation
import kotlinx.coroutines.*
import kotlin.reflect.KProperty

class SettingMutableState<T>(
    initial: T,
    private vararg val onChanged: () -> Unit,
    private val onSet: (oldValue: T, newValue: T) -> Unit = { _, _ -> },
) {
    private val state = mutableStateOf(initial)
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        val old = state.value
        if (old != value) {
            state.value = value
            onChanged.forEach { it() }
            onSet(old, value) // 调用自定义逻辑
        }
    }
}

object AppSettings {
    private val radioSettings get() = SettingsStore.radioSettings
    private fun saveRadioSettings(settings: RadioSettings) = SettingsStore.saveRadioSettings(settings)
    private fun saveRadioSources(sources: List<RadioSource>) = SettingsStore.saveRadioSources(sources)
    private fun savePlaybackState(state: PlaybackState) = SettingsStore.savePlaybackState(state)

    var radioMode by SettingMutableState(radioSettings.radioMode, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(radioMode = new))
    }
    var playMode by SettingMutableState(radioSettings.playMode, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(playMode = new))
    }

    var stingerProbability by SettingMutableState(radioSettings.stingerProbability, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(stingerProbability = new))
    }
    var djProbability by SettingMutableState(radioSettings.djProbability, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(djProbability = new))
    }

    var djGameEventsJson by SettingMutableState(radioSettings.djGameEventsJson, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(djGameEventsJson = new))
    }
    var djGameEvents by SettingMutableState(radioSettings.djGameEvents, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.withDjGameEvents(new))
    }

    val allDjGameEvents: List<String>
        get() = radioSources.values
            .asSequence()
            .flatten()
            .flatMap { it.djSamples }
            .map { it.gameEvent }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()

    var crossListsJson by SettingMutableState(radioSettings.crossListsJson, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(crossListsJson = new))
    }
    var crossLists by SettingMutableState(radioSettings.crossLists, Radio::reset) { _, new ->
        // parsed
        saveRadioSettings(radioSettings.withCrossLists(new))
    }

    var maxContinuousTrack by SettingMutableState(radioSettings.maxContinuousTrack, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(maxContinuousTrack = new))
    }
    var maxContinuousStinger by SettingMutableState(radioSettings.maxContinuousStinger, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(maxContinuousStinger = new))
    }
    var maxContinuousDj by SettingMutableState(radioSettings.maxContinuousDj, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(maxContinuousDj = new))
    }

    var patternEnabled by SettingMutableState(radioSettings.patternEnabled, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(patternEnabled = new))
    }
    var patternJson by SettingMutableState(radioSettings.patternJson, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(patternJson = new))
    }
    var patternNodes by SettingMutableState(radioSettings.patternNodes, Radio::reset) { _, new ->
        // parsed
        saveRadioSettings(radioSettings.withPatternNodes(new))
    }

    var crossFadeEnabled by SettingMutableState(radioSettings.crossFadeEnabled, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.copy(crossFadeEnabled = new))
    }

    var excludedTrackSuffixesJson by SettingMutableState(
        radioSettings.excludedTrackSuffixesJson,
        Radio::reset,
    ) { _, new ->
        saveRadioSettings(radioSettings.copy(excludedTrackSuffixesJson = new))
    }
    var excludedTrackSuffixes by SettingMutableState(radioSettings.excludedTrackSuffixes, Radio::reset) { _, new ->
        saveRadioSettings(radioSettings.withExcludedTrackSuffixes(new))
    }

    var volume by SettingMutableState(radioSettings.volume) { _, new ->
        saveRadioSettings(radioSettings.copy(volume = new))
    }
    var autoResume by SettingMutableState(radioSettings.autoResume) { _, new ->
        saveRadioSettings(radioSettings.copy(autoResume = new))
    }

    var lastStationXmlPath by SettingMutableState(radioSettings.lastStationXmlPath) { _, new ->
        saveRadioSettings(radioSettings.copy(lastStationXmlPath = new))
    }
    var lastStationName by SettingMutableState(radioSettings.lastStationName) { _, new ->
        saveRadioSettings(radioSettings.copy(lastStationName = new))
    }

    var radioSourcesXml by SettingMutableState(SettingsStore.radioSourcesXml) { _, new ->
        saveRadioSources(new)
    }

    var playbackState by SettingMutableState(SettingsStore.playbackState) { _, new ->
        savePlaybackState(new)
    }

    // 超级重, 不序列化, 启动时读取 RadioInfo.xml 解析构建
    // xml path to stations
    var radioSources: Map<String, List<RadioStation>> by mutableStateOf(emptyMap())
        private set

    fun addRadioSource(source: RadioSource, stations: List<RadioStation>) {
        radioSourcesXml = radioSourcesXml + source
        radioSources = radioSources + (source.xmlFilePath to stations)
    }

    fun updateRadioSource(source: RadioSource) {
        radioSourcesXml = radioSourcesXml.map {
            // 根据 xml path 判断更新指定的源
            if (it.xmlFilePath == source.xmlFilePath) source
            else it
        }
    }

    fun removeRadioSource(xmlFilePath: String) {
        val removedStations = radioSources[xmlFilePath] ?: emptyList()

        radioSourcesXml = radioSourcesXml.filter { it.xmlFilePath != xmlFilePath }
        radioSources = radioSources - xmlFilePath

        if (Radio.selectedStation in removedStations)
            Radio.setStation(null)
    }

    // 从已持久化的 source 列表恢复电台数据
    fun restoreFromPaths(): Job? {
        if (radioSourcesXml.isEmpty()) return null
        return CoroutineScope(Dispatchers.Default).launch {
            val newStations = mutableMapOf<String, List<RadioStation>>()
            for (source in radioSourcesXml) {
                val xml = readFileTextOrNull(source.xmlFilePath) ?: continue
                val result = RadioXmlParser.parse(xml)
                AudioScanner().verifyOnly(result, source.audioFolderPath)
                newStations[source.xmlFilePath] = result.stations
            }
            withContext(Dispatchers.Main) {
                radioSources = newStations
            }
        }
    }

    fun RadioStation.getSource(): RadioSource? {
        for ((xmlPath, stations) in radioSources) {
            if (stations.any { it === this })
                return radioSourcesXml
                    .find { it.xmlFilePath == xmlPath }
        }
        return null
    }

    fun saveLastStation(station: RadioStation?) {
        if (station == null) return saveRadioSettings(
            radioSettings.copy(
                lastStationXmlPath = null,
                lastStationName = null,
            ),
        )

        val source = station.getSource() ?: return
        val xmlPath = source.xmlFilePath
        saveRadioSettings(
            radioSettings.copy(
                lastStationXmlPath = xmlPath,
                lastStationName = station.name,
            ),
        )
    }
}
