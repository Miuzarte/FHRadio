package io.github.miuzarte.fhradio

import com.russhwolf.settings.Settings
import io.github.miuzarte.fhradio.model.PlayListType
import io.github.miuzarte.fhradio.model.PlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

object SettingsStore {
    private val s: Settings = Settings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // for sources
    internal val json = Json { ignoreUnknownKeys = true }

    fun loadSettings() = RadioSettings(
        stingerChance = s.getInt("stinger_chance", 10),
        djChance = s.getInt("dj_chance", 1),
        // playListType = PlayListType.valueOf(s.getString("playlist_type", PlayListType.Track.name)),
        playMode = PlayMode.valueOf(s.getString("play_mode", PlayMode.Shuffle.name)),
        radioMode = RadioMode.valueOf(s.getString("radio_mode", RadioMode.Random.name)),
        sourcesJson = s.getString("sources_json", "[]"),
        autoResume = s.getBoolean("auto_resume", false),
        lastStationXmlPath = s.getStringOrNull("last_station_xml"),
        lastStationName = s.getStringOrNull("last_station_name"),
        patternEnabled = s.getBoolean("pattern_enabled", false),
        patternJson = s.getString("pattern_json", "[]"),
        crossListsJson = s.getString("cross_lists_json", """["Track"]"""),
        crossFadeEnabled = s.getBoolean("cross_fade_enabled", true),
    )

    fun saveSettings(settings: RadioSettings) {
        s.putInt("stinger_chance", settings.stingerChance)
        s.putInt("dj_chance", settings.djChance)
        // s.putString("playlist_type", settings.playListType.name)
        s.putString("play_mode", settings.playMode.name)
        s.putString("radio_mode", settings.radioMode.name)
        s.putString("sources_json", settings.sourcesJson)
        s.putBoolean("auto_resume", settings.autoResume)

        val xml = settings.lastStationXmlPath
        if (xml != null) s.putString("last_station_xml", xml)
        else s.remove("last_station_xml")

        val name = settings.lastStationName
        if (name != null) s.putString("last_station_name", name)
        else s.remove("last_station_name")

        s.putBoolean("pattern_enabled", settings.patternEnabled)
        s.putString("pattern_json", settings.patternJson)
        s.putString("cross_lists_json", settings.crossListsJson)
        s.putBoolean("cross_fade_enabled", settings.crossFadeEnabled)
    }

    fun loadSources(): List<RadioSource> = runCatching {
        json.decodeFromString<List<RadioSource>>(s.getString("sources_json", "[]"))
    }.getOrDefault(emptyList())

    fun saveSources(sources: List<RadioSource>) {
        s.putString("sources_json", json.encodeToString<List<RadioSource>>(sources))
    }

    data class PlaybackState(
        val soundName: String? = null,
        val positionMs: Long = 0L,
        val type: String? = null,
    )

    private var cachedPlaybackState = PlaybackState(
        type = s.getStringOrNull("last_playback_type"),
        soundName = s.getStringOrNull("last_playback_sound_name"),
        positionMs = s.getLong("last_playback_position_ms", 0L),
    )

    fun loadPlaybackState() = cachedPlaybackState

    fun savePlaybackState(soundName: String?, posMs: Long, type: String?) {
        cachedPlaybackState = PlaybackState(soundName, posMs, type)
        scope.launch {
            if (type != null) s.putString("last_playback_type", type)
            else s.remove("last_playback_type")

            if (soundName != null) s.putString("last_playback_sound_name", soundName)
            else s.remove("last_playback_sound_name")

            s.putLong("last_playback_position_ms", posMs)
        }
    }
}
