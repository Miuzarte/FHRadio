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

internal fun debugDo(block: () -> Unit) {
    if (BuildKonfig.DEBUG) block()
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

    internal fun sampleList(type: SampleType): List<Sample>? = when (type) {
        SampleType.Track -> selectedTracks
        SampleType.Stinger -> selectedStingers
        SampleType.DJ -> selectedDj
    }

    var currentTrack: TrackSample? by mutableStateOf(null)
    internal lateinit var lastTrack: TrackSample
    var currentStinger: StingerSample? by mutableStateOf(null)
    internal lateinit var lastStinger: StingerSample
    var currentDj: DjSample? by mutableStateOf(null)
    internal lateinit var lastDj: DjSample

    // ===== Engine =====

    internal var modeEngine: RadioModeEngine = RandomEngine()
        private set

    fun buildEngine() {
        modeEngine = when (settings.radioMode) {
            RadioMode.Random -> RandomEngine()
            RadioMode.Player -> PlayerEngine()
            RadioMode.Seed -> SeedEngine()
        }
        reschedule()
    }

    // ===== Coroutine scope =====

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ===== Station lifecycle =====

    fun openStation(station: RadioStation, play: Boolean = true) {
        closeStation()
        selectedStation = station
        AppSettings.saveLastStation(station)
        if (play) modeEngine.resume()
    }

    fun closeStation() {
        stopPlayback()
        selectedStation = null
    }

    // ===== Scheduling infrastructure =====

    private data class TaggedJob(
        val job: Job,
        val tag: String,
    )

    private data class DebugMeta(
        val targetPos: Int,
        val time: Duration,
        val delay: Duration,
        val scheduledAt: Instant,
    )

    private val scheduledJobs = mutableListOf<Job>()
    private val taggedJobs = mutableListOf<TaggedJob>()
    private val debugMetas = mutableMapOf<String, DebugMeta>()

    data class ScheduledInfo(
        val tag: String,
        val targetPos: Int,
        val total: Duration,
        val remain: Duration,
        val fireAt: Instant,
    )

    var debugScheduledMarkers: List<ScheduledInfo> by mutableStateOf(emptyList())
        private set

    fun syncDebugMarkers() {
        val now = Clock.System.now()
        debugScheduledMarkers = debugMetas.map { (tag, meta) ->
            val elapsed = now - meta.scheduledAt
            val remain = (meta.delay - elapsed).coerceAtLeast(Duration.ZERO)
            ScheduledInfo(tag, meta.targetPos, meta.time, remain, meta.scheduledAt + meta.delay)
        }
    }

    fun refreshDebugMarkers() {
        debugDo { syncDebugMarkers() }
    }

    private fun cancelScheduledByTag(tag: String) {
        val iter = taggedJobs.iterator()
        while (iter.hasNext()) {
            val tj = iter.next()
            if (tj.tag == tag) {
                tj.job.cancel()
                scheduledJobs.remove(tj.job)
                iter.remove()
            }
        }
        debugMetas.remove(tag)
        syncDebugMarkers()
        debugDo { AppRuntime.snackbar("取消: $tag") }
    }

    internal fun cancelAllScheduled() {
        scheduledJobs.forEach { it.cancel() }
        scheduledJobs.clear()
        taggedJobs.clear()
        debugMetas.clear()
        syncDebugMarkers()
        debugDo { AppRuntime.snackbar("取消: 全部") }
    }

    private fun schedule(tag: String, delay: Duration, block: suspend CoroutineScope.() -> Unit) {
        val job = if (!delay.isPositive()) {
            scope.launch(Dispatchers.Default, block = block)
        } else {
            scope.launch(Dispatchers.Default) {
                delay(delay)
                block()
            }
        }
        scheduledJobs.add(job)
        taggedJobs.add(TaggedJob(job, tag))
    }

    internal fun scheduleMarker(
        tag: String,
        sample: Sample,
        targetPos: Int,
        beginAt: Duration,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        val full = sample.samplesToDuration(targetPos)
        val delay = full - beginAt
        val scheduledAt = Clock.System.now()
        schedule(tag, delay.coerceAtLeast(Duration.ZERO), block)
        debugMetas[tag] = DebugMeta(targetPos, full, delay, scheduledAt)
        syncDebugMarkers()
        debugDo {
            val d = delay.coerceAtLeast(Duration.ZERO)
            val totalSec = d.inWholeSeconds
            val min = totalSec / 60
            val sec = totalSec % 60
            val timeStr = """${if (min > 0) "${min}m" else ""}${sec}s"""
            val note = if (!delay.isPositive()) " 立即" else ""
            AppRuntime.snackbar("派发: $tag | ${timeStr}后 | @$targetPos$note")
        }
    }

    private fun scheduleEndMarkers(sample: Sample, beginAt: Duration) {
        when (sample) {
            is TrackSample -> {
                val tag = if (sample.end > 0) "Track.End" else "Track.SampleLength"
                val targetPos = if (sample.end > 0) sample.end else sample.sampleLength
                scheduleMarker(tag, sample, targetPos, beginAt) {
                    debugDo { AppRuntime.snackbar("$tag @ $targetPos") }
                    onTrackEnd()
                }
            }

            is StingerSample -> {
                val tag = if (sample.end > 0) "Stinger.End" else "Stinger.SampleLength"
                val targetPos = if (sample.end > 0) sample.end else sample.sampleLength
                scheduleMarker(tag, sample, targetPos, beginAt) {
                    debugDo { AppRuntime.snackbar("$tag @ $targetPos") }
                    onStingerEnd()
                }
            }

            is DjSample -> {
                scheduleMarker("DJ.SampleLength", sample, sample.sampleLength, beginAt) {
                    debugDo { AppRuntime.snackbar("DJ.SampleLength @ ${sample.sampleLength}") }
                    onDjEnd()
                }
            }
        }
    }

    private fun scheduleMarkers(sample: Sample, beginAt: Duration = Duration.ZERO) {
        modeEngine.scheduleModeMarkers(sample, beginAt)
        scheduleEndMarkers(sample, beginAt)
    }

    // ===== Callbacks =====

    private fun onTrackEnd() {
        currentTrack = null
        if (currentStinger == null && currentDj == null)
            modeEngine.advance()
    }

    private fun stingerPlayerIsBusy(): Boolean {
        val s = secondaryPlayer.getState()
        return s.status == PlaybackStatus.Playing ||
                s.status == PlaybackStatus.Buffering ||
                s.status == PlaybackStatus.Opening
    }

    internal fun onStingerStart() {
        val stinger = selectedStingers
            ?.filter { it.resolvePath() != null }
            ?.randomOrNull()
            ?: return
        if (beginSample(stinger, solo = false, useTryPlay = true)) {
            cancelScheduledByTag("DJStart")
            cancelScheduledByTag("TrackEnd")
        }
    }

    internal fun onStingerNextTrack() {
        modeEngine.advance()
    }

    internal fun onStingerEnd() {
        currentStinger = null
        val track = modeEngine.nextSample(SampleType.Track)
        if (track != null) beginSample(track, solo = false, useTryPlay = true)
    }

    internal fun onDjStart() {
        if (currentTrack == null) return
        if (stingerPlayerIsBusy()) return
        val dj = selectedDj
            ?.filter { it.resolvePath() != null }
            ?.randomOrNull()
            ?: return
        beginSample(dj, solo = false, useTryPlay = true)
    }

    internal fun onDjEnd() {
        currentDj = null
        modeEngine.advance()
    }

    // ===== Playback control =====

    internal var lastPlayedSample: Sample? = null
    private var periodicSaveJob: Job? = null

    private var trackStartedAt: Instant? = null
    private var trackBeginAt: Duration = Duration.ZERO
    private var stingerStartedAt: Instant? = null
    private var stingerBeginAt: Duration = Duration.ZERO
    private var djStartedAt: Instant? = null
    private var djBeginAt: Duration = Duration.ZERO

    private var lastRescheduleAt: Instant? = null

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
        modeEngine.onSamplePlayed(sample)
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
            AppRuntime.snackbar(
                """${if (useTryPlay) "(try) " else ""}切入 ${sample.type}: $timeStr (${samples})"""
            )
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
        cancelAllScheduled()
        finishPlayback()
    }

    internal fun stopMain() {
        mainPlayer.stop()
        cancelScheduledByTag("Track.StingerStart")
        cancelScheduledByTag("Track.DJStart")
        cancelScheduledByTag("Track.End")
        cancelScheduledByTag("Track.SampleLength")
    }

    internal fun stopSecondary() {
        secondaryPlayer.stop()
        cancelScheduledByTag("Stinger.StartNextTrack")
        cancelScheduledByTag("Stinger.End")
        cancelScheduledByTag("Stinger.SampleLength")
        cancelScheduledByTag("DJ.SampleLength")
    }

    private fun finishPlayback() {
        stopPeriodicSave()
        mainPlayer.stop()
        secondaryPlayer.stop()
        currentTrack = null
        currentStinger = null
        currentDj = null
    }

    // ===== Position tracking =====

    internal fun currentTrackPos(): Duration =
        trackStartedAt?.let { trackBeginAt + (Clock.System.now() - it) } ?: Duration.ZERO

    internal fun currentStingerPos(): Duration =
        stingerStartedAt?.let { stingerBeginAt + (Clock.System.now() - it) } ?: Duration.ZERO

    internal fun currentDjPos(): Duration =
        djStartedAt?.let { djBeginAt + (Clock.System.now() - it) } ?: Duration.ZERO

    fun reschedule() {
        val now = Clock.System.now()
        if (lastRescheduleAt != null && (now - lastRescheduleAt!!) < 300.milliseconds) return
        lastRescheduleAt = now
        cancelAllScheduled()

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

    fun resetPatternState() = modeEngine.resetPatternState()

    // ===== Helpers shared by engines =====

    internal fun playNext(type: SampleType, beginAt: Duration = Duration.ZERO) {
        val sample = modeEngine.nextSample(type) ?: return
        beginSample(sample, beginAt, solo = false)
    }

    internal fun playRandom(type: SampleType) {
        val sample = modeEngine.nextSample(type) ?: return
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

    // ===== Utilities =====

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
        cancelAllScheduled()
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
