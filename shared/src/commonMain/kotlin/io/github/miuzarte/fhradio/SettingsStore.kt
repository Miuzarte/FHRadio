package io.github.miuzarte.fhradio

import com.russhwolf.settings.Settings
import io.github.miuzarte.fhradio.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

object SettingsStore {
    private val s: Settings = Settings()

    // 单线程异步执行保存
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    var radioSettings =
        RadioSettings(
            // radio
            radioMode = RadioMode.valueOf(s.getString("radio_mode", RadioMode.Random.name)),
            playMode = PlayMode.valueOf(s.getString("play_mode", PlayMode.Shuffle.name)),

            stingerProbability = s.getInt("stinger_probability", RadioSettings.defaults.stingerProbability),
            djProbability = s.getInt("dj_probability", RadioSettings.defaults.djProbability),

            crossListsJson = s.getString("cross_lists_json", RadioSettings.defaults.crossListsJson),

            patternEnabled = s.getBoolean("pattern_enabled", RadioSettings.defaults.patternEnabled),
            patternJson = s.getString("pattern_json", RadioSettings.defaults.patternJson),

            crossFadeEnabled = s.getBoolean("cross_fade_enabled", RadioSettings.defaults.crossFadeEnabled),

            // application
            autoResume = s.getBoolean("auto_resume", RadioSettings.defaults.autoResume),

            // internal
            lastStationXmlPath = s.getStringOrNull("last_station_xml"),
            lastStationName = s.getStringOrNull("last_station_name"),
        )
        private set

    fun saveRadioSettings(settings: RadioSettings) {
        radioSettings = settings

        scope.launch {
            s.putString("radio_mode", settings.radioMode.name)
            s.putString("play_mode", settings.playMode.name)

            s.putInt("stinger_probability", settings.stingerProbability)
            s.putInt("dj_probability", settings.djProbability)

            s.putString("cross_lists_json", settings.crossListsJson)

            s.putBoolean("pattern_enabled", settings.patternEnabled)
            s.putString("pattern_json", settings.patternJson)

            s.putBoolean("cross_fade_enabled", settings.crossFadeEnabled)

            s.putBoolean("auto_resume", settings.autoResume)

            val xml = settings.lastStationXmlPath
            if (xml != null) s.putString("last_station_xml", xml)
            else s.remove("last_station_xml")

            val name = settings.lastStationName
            if (name != null) s.putString("last_station_name", name)
            else s.remove("last_station_name")
        }
    }

    // --- radio sources ---
    // 有点重, 单独放

    private val json = Json { ignoreUnknownKeys = true }

    var radioSourcesXml =
        runCatching {
            val sourcesStr = s.getString("sources_json", "[]")
            json.decodeFromString<List<RadioSource>>(sourcesStr)
        }.getOrDefault(emptyList())
        private set

    fun saveRadioSources(sources: List<RadioSource>) {
        radioSourcesXml = sources

        scope.launch {
            val sourcesJson = json.encodeToString<List<RadioSource>>(sources)
            s.putString("sources_json", sourcesJson)
        }
    }

    // --- playback state ---
    // 频率高, 单独放

    var playbackState =
        PlaybackState(
            soundName = s.getStringOrNull("last_playback_sound_name"),
            position = s.getLong("last_playback_position_ms", 0L).milliseconds,
            sampleType = s.getStringOrNull("last_playback_sample_type")?.let(SampleType::valueOf),
        )
        private set

    fun savePlaybackState(state: PlaybackState) {
        playbackState = state

        scope.launch {
            if (state.soundName != null) s.putString("last_playback_sound_name", state.soundName)
            else s.remove("last_playback_sound_name")

            s.putLong("last_playback_position_ms", state.position.inWholeMilliseconds)

            if (state.sampleType != null) s.putString("last_playback_sample_type", state.sampleType.toString())
            else s.remove("last_playback_sample_type")
        }
    }
}
