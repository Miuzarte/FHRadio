package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.DjSample
import io.github.miuzarte.fhradio.model.PlaybackStatus
import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.model.StingerSample
import io.github.miuzarte.fhradio.model.TrackSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlin.time.toDuration
import okio.Path.Companion.toPath
import top.yukonga.miuix.kmp.basic.SnackbarDuration

internal fun debugDo(block: () -> Unit) {
    if (BuildKonfig.DEBUG) block()
}

internal fun debugSnack(message: String) {
    debugDo {
        AppRuntime.snackbar(
            message = message,
            duration = SnackbarDuration.Long,
        )
    }
}

object Radio {
    private val mainPlayer get() = AppRuntime.mainPlayer
    private val secondaryPlayer get() = AppRuntime.secondaryPlayer
    internal val settings get() = AppSettings.radioSettings
    internal val crossLists get() = AppSettings.crossLists
    internal val patternNodes get() = AppSettings.loadPatternNodes()

    var selectedStation: RadioStation? by mutableStateOf(null)
    internal val selectedTracks get() = selectedStation?.tracks
    internal val selectedStingers get() = selectedStation?.stingers
    internal val selectedDj get() = selectedStation?.djSamples

    internal var modeEngine: RadioModeEngine? = null
        private set

    fun getPlaylist() = modeEngine?.getPlaylist()

    internal fun sampleList(type: SampleType): List<Sample>? =
        when (type) {
            SampleType.Track -> selectedTracks
            SampleType.Stinger -> selectedStingers
            SampleType.DJ -> selectedDj
        }

    var pendingTrack: TrackSample? by mutableStateOf(null)
    var currentTrack: TrackSample? by mutableStateOf(null)
    internal lateinit var lastTrack: TrackSample
    var pendingStinger: StingerSample? by mutableStateOf(null)
    var currentStinger: StingerSample? by mutableStateOf(null)
    internal lateinit var lastStinger: StingerSample
    var pendingDj: DjSample? by mutableStateOf(null)
    var currentDj: DjSample? by mutableStateOf(null)
    internal lateinit var lastDj: DjSample


    // --- Coroutine scope ---

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- Station lifecycle ---

    fun openStation(
        station: RadioStation,
        play: Boolean = true,
    ) {
        closeStation()
        selectedStation = station
        buildEngine(station)
        AppSettings.saveLastStation(station)
        if (play) modeEngine?.getResume(SettingsStore.loadPlaybackState())
    }

    fun closeStation() {
        stopPlayback()
        modeEngine = null
        selectedStation = null
    }

    private fun buildEngine(station: RadioStation) {
        modeEngine = when (settings.radioMode) {
            RadioMode.Random -> RandomEngine(
                station = station,
            )

            RadioMode.Player -> PlayerEngine(
                station = station,
                playMode = settings.playMode,
                crossLists = crossLists,
                patternEnabled = settings.patternEnabled,
                patternNodes = patternNodes,
            )

            RadioMode.Seed -> SeedEngine(
                station = station,
            )
        }
        reschedule()
    }

    // private fun releaseEngin() { modeEngine = null }

    // --- Scheduling infrastructure ---

    // 让不同的 Engine 自行派发
    private fun scheduleMarkers(sample: Sample, beginAt: Duration = Duration.ZERO) {
        modeEngine?.scheduleModeMarkers(sample, beginAt) ?: return
        scheduleEndMarkers(sample, beginAt)
    }

    // 每个 Sample 都有 .End marker, 统一在这里派发
    private fun scheduleEndMarkers(sample: Sample, beginAt: Duration) =
        when (sample) {
            is TrackSample -> {
                val tag = if (sample.end > 0) "Track.End" else "Track.SampleLength"
                val targetPos = if (sample.end > 0) sample.end else sample.sampleLength
                Scheduler.scheduleMarker(tag, sample, targetPos, beginAt) {
                    debugSnack("$tag @ $targetPos")
                    trackOnEnd()
                }
            }

            is StingerSample -> {
                val tag = if (sample.end > 0) "Stinger.End" else "Stinger.SampleLength"
                val targetPos = if (sample.end > 0) sample.end else sample.sampleLength
                Scheduler.scheduleMarker(tag, sample, targetPos, beginAt) {
                    debugSnack("$tag @ $targetPos")
                    stingerOnEnd()
                }
            }

            is DjSample -> {
                val tag = "DJ.SampleLength"
                val targetPos = sample.sampleLength
                Scheduler.scheduleMarker(tag, sample, targetPos, beginAt) {
                    debugSnack("$tag @ $targetPos")
                    djOnEnd()
                }
            }
        }

    // --- Callbacks ---

    private fun stingerPlayerIsBusy() = secondaryPlayer.state.isBusy()

    // "Track.End" / "Track.SampleLength"
    // 处理 currentTrack 播放结束后的收尾
    private fun trackOnEnd() {
        currentTrack = null
    }

    // "Track.StingerStart"
    // 根据 pendingStinger 来播放
    private fun trackOnStingerStart() {
    }

    // "Track.DJStart"
    private fun trackOnDJStart() {
    }

    // "Stinger.StartNextTrack"
    private fun stingerOnStartNextTrack() {
    }

    // "Stinger.End" / "Stinger.SampleLength"
    private fun stingerOnEnd() {
        currentStinger = null
    }

    // "DJ.SampleLength"
    private fun djOnEnd() {
        currentDj = null
    }

    // --- Playback control ---

    internal var lastPlayedSample: Sample? = null
    private var periodicSaveJob: Job? = null

    private var trackStartedAt: Instant? = null
    private var trackBeginAt: Duration = Duration.ZERO
    private var stingerStartedAt: Instant? = null
    private var stingerBeginAt: Duration = Duration.ZERO
    private var djStartedAt: Instant? = null
    private var djBeginAt: Duration = Duration.ZERO


    fun playRandomFromList(type: SampleType) {
        selectedStation ?: return
        if (currentTrack != null || currentStinger != null || currentDj != null) return
        val list = sampleList(type) ?: return
        val sample = list.filter { it.resolvePath() != null }.randomOrNull()
        if (sample != null) playSample(sample)
        else AppRuntime.snackbar("无可用曲目")
    }

    fun playSample(sample: Sample): Boolean {
        selectedStation ?: return false
        lastPlayedSample = sample
        return beginSample(sample)
    }

    fun beginSample(
        sample: Sample,
        beginAt: Duration = Duration.ZERO,
        solo: Boolean = true,
        useTryPlay: Boolean = false,
    ): Boolean {
        selectedStation ?: return false
        if (solo) {
            stopMain()
            stopSecondary()
        }
        val path = sample.resolvePath()
        if (path == null) {
            finishPlayback()
            return false
        }

        lastPlayedSample = sample

        when (sample) {
            is TrackSample -> {
                currentTrack = sample
                lastTrack = sample
                currentStinger = null
                currentDj = null
                trackStartedAt = Clock.System.now()
                trackBeginAt = beginAt
                if (useTryPlay) mainPlayer.tryPlay(path, beginAt)
                else mainPlayer.play(path, beginAt)
            }

            is StingerSample -> {
                currentStinger = sample
                lastStinger = sample
                currentTrack = null
                currentDj = null
                stingerStartedAt = Clock.System.now()
                stingerBeginAt = beginAt
                if (useTryPlay) secondaryPlayer.tryPlay(path, beginAt)
                else secondaryPlayer.play(path, beginAt)
            }

            is DjSample -> {
                currentDj = sample
                lastDj = sample
                currentTrack = null
                currentStinger = null
                djStartedAt = Clock.System.now()
                djBeginAt = beginAt
                if (useTryPlay) secondaryPlayer.tryPlay(path, beginAt)
                else secondaryPlayer.play(path, beginAt)
            }
        }

        debugDo {
            val totalSec = beginAt.inWholeSeconds
            val min = totalSec / 60
            val sec = totalSec % 60
            val timeStr = """${if (min > 0) "${min}m" else ""}${sec}s"""
            val samples = beginAt.inWholeMilliseconds * sample.sampleRate / 1000
            debugSnack("""${if (useTryPlay) "(try) " else ""}切入 ${sample.type}: $timeStr (${samples})""")
        }

        scheduleMarkers(sample, beginAt)
        startPeriodicSave()
        return true
    }

    private fun startPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000.milliseconds)
                when {
                    currentTrack != null -> {
                        val t = currentTrack!!
                        SettingsStore.savePlaybackState(t.soundName, currentTrackPos(), "Track")
                    }

                    currentStinger != null -> {
                        val s = currentStinger!!
                        SettingsStore.savePlaybackState(s.soundName, currentStingerPos(), "Stinger")
                    }

                    currentDj != null -> {
                        val d = currentDj!!
                        SettingsStore.savePlaybackState(d.soundName, currentDjPos(), "DJ")
                    }
                }
            }
        }
    }

    private fun stopPeriodicSave() {
        periodicSaveJob?.cancel()
        periodicSaveJob = null
    }

    fun stopPlayback() {
        stopPeriodicSave()
        val st = selectedStation
        val tr = currentTrack
        if (st != null && tr != null) {
            AppSettings.saveLastStation(st)
        }
        Scheduler.cancel()
        finishPlayback()
    }

    internal fun stopMain() {
        mainPlayer.stop()
        Scheduler.cancel("Track.StingerStart")
        Scheduler.cancel("Track.DJStart")
        Scheduler.cancel("Track.End")
        Scheduler.cancel("Track.SampleLength")
    }

    internal fun stopSecondary() {
        secondaryPlayer.stop()
        Scheduler.cancel("Stinger.StartNextTrack")
        Scheduler.cancel("Stinger.End")
        Scheduler.cancel("Stinger.SampleLength")
        Scheduler.cancel("DJ.SampleLength")
    }

    private fun finishPlayback() {
        stopPeriodicSave()
        mainPlayer.stop()
        secondaryPlayer.stop()
        currentTrack = null
        currentStinger = null
        currentDj = null
    }

    // --- Position tracking ---

    internal fun currentTrackPos(): Duration =
        trackStartedAt?.let { trackBeginAt + (Clock.System.now() - it) } ?: Duration.ZERO

    internal fun currentStingerPos(): Duration =
        stingerStartedAt?.let { stingerBeginAt + (Clock.System.now() - it) } ?: Duration.ZERO

    internal fun currentDjPos(): Duration =
        djStartedAt?.let { djBeginAt + (Clock.System.now() - it) } ?: Duration.ZERO

    private var lastRescheduleAt: Instant? = null

    fun reschedule() {
        val now = Clock.System.now()
        if (lastRescheduleAt != null && (now - lastRescheduleAt!!) < 300.milliseconds) return
        lastRescheduleAt = now
        Scheduler.cancel()

        if (trackStartedAt != null) {
            currentTrack?.let { scheduleMarkers(it, currentTrackPos()) }
        }
        if (stingerStartedAt != null) {
            currentStinger?.let { scheduleMarkers(it, currentStingerPos()) }
        }
        if (djStartedAt != null) {
            currentDj?.let { scheduleMarkers(it, currentDjPos()) }
        }
    }

    fun reset() = modeEngine.reset()

    // --- Helpers shared by engines ---

    internal fun playNext(type: SampleType, beginAt: Duration = Duration.ZERO) {
        val sample = modeEngine.getNext(type) ?: return
        beginSample(sample, beginAt, solo = false)
    }

    internal fun playRandom(type: SampleType) {
        val sample = modeEngine.getNext(type) ?: return
        beginSample(sample, sample.randomBeginAt(), solo = false)
    }

    internal fun randomSample(type: SampleType, exclude: Set<Sample> = emptySet()): Sample? {
        val list = sampleList(type) ?: return null
        if (list.all { it in exclude }) return null
        var sample: Sample
        do {
            sample = list[Random.nextInt(list.size)]
        } while (sample in exclude)
        return sample
    }

    // --- Utilities ---

    private fun Sample.samplesToDuration(pos: Int): Duration =
        (pos * 1000L / sampleRate).toDuration(DurationUnit.MILLISECONDS)

    private fun Sample.randomBeginAt(): Duration = when (this) {
        is TrackSample -> {
            if (this.durationMs <= 1000) Duration.ZERO
            else if (this.trackLoopStart <= 0) Random.nextLong(this.durationMs).toDuration(DurationUnit.MILLISECONDS)
            else {
                val loopStartMs = trackLoopStart * 1000L / this.sampleRate
                if (Random.nextInt(100) < 40) {
                    val offset = Random.nextLong(-loopStartMs, loopStartMs + 1)
                    (loopStartMs + offset).coerceIn(0, this.durationMs - 1000).toDuration(DurationUnit.MILLISECONDS)
                } else {
                    Random.nextLong(this.durationMs).toDuration(DurationUnit.MILLISECONDS)
                }
            }
        }

        is StingerSample -> Random.nextLong(0, durationMs / 3).toDuration(DurationUnit.MILLISECONDS)
        is DjSample -> Random.nextLong(0, durationMs / 2).toDuration(DurationUnit.MILLISECONDS)
    }

    fun dispose() {
        Scheduler.cancel()
    }
}

internal fun Sample.resolvePath(): String? {
    val station = Radio.selectedStation ?: return null
    val root = AppSettings.findSourcePath(station) ?: return null
    val (sub, name) = when (this) {
        is TrackSample -> "Track" to this.soundName
        is DjSample -> {
            val idx = Radio.sampleList(this.type)?.indexOf(this) ?: return null
            "DJ" to if (idx < 0) return null else "sound_$idx"
        }

        is StingerSample -> {
            val idx = Radio.sampleList(this.type)?.indexOf(this) ?: return null
            "Stinger" to if (idx < 0) return null else "sound_$idx"
        }
    }
    val base = (root.toPath() / station.name / sub / name).toString()

    val primaryExt = AppSettings.findSourceExtension(station)
    var path = "$base.$primaryExt"
    if (fileExists(path)) return path
    for (ext in listOf("wav", "flac", "mp3", "opus")) {
        if (ext == primaryExt) continue
        path = "$base.$ext"
        if (fileExists(path)) return path
    }
    return null
}
