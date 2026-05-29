package io.github.miuzarte.fhradio

import com.russhwolf.settings.Settings
import io.github.miuzarte.fhradio.model.PlaybackState
import io.github.miuzarte.fhradio.model.RadioSettings
import io.github.miuzarte.fhradio.model.RadioSourceConfig
import io.github.miuzarte.fhradio.model.SampleType
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

object SettingsStore {
    private val s: Settings = platformSettings()

    // 单线程异步执行保存
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val json = Json { ignoreUnknownKeys = true }

    // --- settings ---

    var radioSettings =
        runCatching {
            s.getStringOrNull("radioSettings")?.let {
                json.decodeFromString<RadioSettings>(it)
            } ?: RadioSettings.defaults
        }.getOrDefault(RadioSettings.defaults)
        private set

    fun saveRadioSettings(settings: RadioSettings) {
        radioSettings = settings
        scope.launch {
            s.putString("radioSettings", json.encodeToString(settings))
        }
    }

    // --- radio sources ---
    // 有点重, 单独放

    var radioSourcesConfig =
        runCatching {
            val sourcesStr = s.getString("radioSourceConfigs", "[]")
            json.decodeFromString<List<RadioSourceConfig>>(sourcesStr)
        }.getOrDefault(emptyList())
        private set

    fun saveRadioSources(sources: List<RadioSourceConfig>) {
        radioSourcesConfig = sources

        scope.launch {
            val sourcesJson = json.encodeToString<List<RadioSourceConfig>>(sources)
            s.putString("radioSourceConfigs", sourcesJson)
        }
    }

    // --- playback state ---
    // 频率高, 单独放

    var playbackState =
        PlaybackState(
            soundName = s.getStringOrNull("playbackStateSoundName"),
            position = s.getLong("playbackStatePosition", 0L).milliseconds,
            sampleType = s.getStringOrNull("playbackStateSampleType")?.let(SampleType::valueOf),
        )
        private set

    fun savePlaybackState(state: PlaybackState) {
        playbackState = state

        scope.launch {
            if (state.soundName != null) s.putString("playbackStateSoundName", state.soundName)
            else s.remove("playbackStateSoundName")

            s.putLong("playbackStatePosition", state.position.inWholeMilliseconds)

            if (state.sampleType != null) s.putString("playbackStateSampleType", state.sampleType.toString())
            else s.remove("playbackStateSampleType")
        }
    }

    // --- playback stats ---

    var totalPlaybackMinutes = s.getLong("totalPlaybackMinutes", 0L)
        private set

    fun savePlaybackMinutes(minutes: Long) {
        totalPlaybackMinutes = minutes
        scope.launch {
            s.putLong("totalPlaybackMinutes", minutes)
        }
    }
}
