package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.AppSettings.getSource
import io.github.miuzarte.fhradio.constants.SUPPORTED_FORMATS
import io.github.miuzarte.fhradio.model.*
import io.github.miuzarte.fhradio.util.formatTime
import kotlinx.coroutines.*
import okio.FileNotFoundException
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal fun debugDo(block: () -> Unit) {
    if (AppRuntime.debug) block()
}

internal fun debugSnack(message: String) {
    val message = "[${Clock.System.now().formatTime(withMs = false)}] $message"
    println(message)
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

    var selectedStation: RadioStation? by mutableStateOf(null)
        private set
    private var modeEngine: RadioModeEngineV2? = null

    fun getPlayList() = modeEngine?.getPlayList()

    // 通过显式置 null 以跳过指定 Sample 的保存
    private fun savePlaybackState(
        track: TrackSample? = trackPlaying,
        stinger: StingerSample? = stingerPlaying,
        dj: DjSample? = djPlaying,
    ) {
        selectedStation ?: return
        track?.let {
            // debugSnack("saving track ${it.soundName} @ $trackCurrentPos")
            AppSettings.playbackState = PlaybackState(
                it.soundName,
                trackCurrentPos ?: Duration.ZERO,
                SampleType.Track,
            )
            return
        }
        stinger?.let {
            // debugSnack("saving stinger ${it.soundName} @ $stingerCurrentPos")
            AppSettings.playbackState = PlaybackState(
                it.soundName,
                stingerCurrentPos ?: Duration.ZERO,
                SampleType.Stinger,
            )
            return
        }
        dj?.let {
            // debugSnack("saving dj ${it.soundName} @ $djCurrentPos")
            AppSettings.playbackState = PlaybackState(
                it.soundName,
                djCurrentPos ?: Duration.ZERO,
                SampleType.DJ,
            )
            return
        }
        // 全空则无视, 留下最后的状态
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 每秒保存播放状态, 用于启动应用时自动恢复
    private var periodicSaveJob: Job? =
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1.seconds)
                savePlaybackState()
            }
        }

    // --- Station lifecycle ---

    fun reset() {
        // require(selectedStation != null) { "no station selected" }

        if (selectedStation == null) return // nothing to do
        modeEngine = selectEngine(selectedStation!!)
        currentSection?.let { modeEngine?.onSectionStarted(it) }
    }

    fun setStation(
        station: RadioStation?, // null for close
        // TODO: 播放器模式下手动切台不自动播放, 程序启动按设置仍然自动播放
        // TODO: 如果是切台, 切之后也继续播放
        play: Boolean = true,
    ) {
        AppSettings.saveLastStation(station)

        // close station
        if (station == null) {
            AppSettings.lastStationName = null
            AppSettings.lastStationXmlPath = null
            Scheduler.cancel()
            stopBothPlayer()
            selectedStation = null
            modeEngine = null
            stopForegroundService()
            return
        }

        // open/switch station
        selectedStation = station
        modeEngine = selectEngine(station)
        // 直接 beginSection(resume) 即可, 不需要 reschedule

        if (play) {
            modeEngine
                ?.resume(AppSettings.playbackState)
                ?.let {
                    val track = it.track?.sample?.soundName to (it.track?.beginAt ?: Duration.ZERO)
                    val stinger = it.stinger?.sample?.soundName to (it.stinger?.beginAt ?: Duration.ZERO)
                    val dj = it.dj?.sample?.soundName to (it.dj?.beginAt ?: Duration.ZERO)
                    debugSnack("resuming: (${track.first} @ ${track.second}), (${stinger.first} @ ${stinger.second}), (${dj.first} @ ${dj.second})")
                    beginSection(it)
                }
        }

        startForegroundService()
    }

    private fun selectEngine(station: RadioStation) =
        when (AppSettings.radioMode) {
            RadioMode.Random -> RandomEngine(
                station = station,
                stingerProbability = AppSettings.stingerProbability,
                djProbability = AppSettings.djProbability,
                djGameEvents = AppSettings.djGameEvents,
                excludedTrackSuffixes = AppSettings.excludedTrackSuffixes,
            )

            RadioMode.Seed -> SeedEngine(
                // TODO: implement
                station = station,
            )

            RadioMode.Player -> PlayerEngine(
                station = station,
                playMode = AppSettings.playMode,
                crossLists = AppSettings.crossLists,
                maxContinuousTrack = AppSettings.maxContinuousTrack,
                maxContinuousStinger = AppSettings.maxContinuousStinger,
                maxContinuousDj = AppSettings.maxContinuousDj,
                patternEnabled = AppSettings.patternEnabled,
                patternNodes = AppSettings.patternNodes,
                excludedTrackSuffixes = AppSettings.excludedTrackSuffixes,
            )
        }

    // --- Playback control ---

    private var currentSection: PlaySection? = null

    // 当前播放
    // 播放开始时间点
    // 播放切入点
    // 播放当前进度

    var trackPlaying: TrackSample? by mutableStateOf(null)
        private set
    internal var trackBeginInstant: Instant? = null
    internal var trackBeginPos: Duration = Duration.ZERO
    internal val trackCurrentPos: Duration?
        get() = trackPlaying?.let { mainPlayer.state.position }
    internal val trackDisplayPos: Duration?
        get() {
            val sample = trackPlaying ?: return null
            val instant = trackBeginInstant ?: return null
            val raw = trackBeginPos + (Clock.System.now() - instant)
            return raw.coerceAtMost(sample.duration)
        }

    var stingerPlaying: StingerSample? by mutableStateOf(null)
        private set
    internal var stingerBeginInstant: Instant? = null
    internal var stingerBeginPos: Duration = Duration.ZERO
    internal val stingerCurrentPos: Duration?
        get() = stingerPlaying?.let { secondaryPlayer.state.position }
    internal val stingerDisplayPos: Duration?
        get() {
            val sample = stingerPlaying ?: return null
            val instant = stingerBeginInstant ?: return null
            val raw = stingerBeginPos + (Clock.System.now() - instant)
            return raw.coerceAtMost(sample.duration)
        }

    var djPlaying: DjSample? by mutableStateOf(null)
        private set
    internal var djBeginInstant: Instant? = null
    internal var djBeginPos: Duration = Duration.ZERO
    internal val djCurrentPos: Duration?
        get() = djPlaying?.let { secondaryPlayer.state.position }
    internal val djDisplayPos: Duration?
        get() {
            val sample = djPlaying ?: return null
            val instant = djBeginInstant ?: return null
            val raw = djBeginPos + (Clock.System.now() - instant)
            return raw.coerceAtMost(sample.duration)
        }

    fun beginSample(
        playItem: PlayItem,
        solo: Boolean = false,
        player: AudioPlayer = if (playItem.sample.type == SampleType.Track) mainPlayer else secondaryPlayer,
        useTryPlay: Boolean = player == secondaryPlayer,
    ): Boolean {
        if (solo) stopBothPlayer()
        else when (this) {
            mainPlayer -> stopMainPlayer()
            secondaryPlayer -> stopSecondaryPlayer()
        }

        val sample = playItem.sample
        val path = selectedStation?.resolvePath(sample) ?: return false
        val beginAt = playItem.beginAt

        return if (useTryPlay) {
            player.tryPlay(path, beginAt)
        } else {
            player.play(path, beginAt)
            true
        }.also {
            if (it) {
                when (sample) {
                    is TrackSample -> {
                        trackPlaying = sample
                        trackBeginInstant = Clock.System.now()
                        trackBeginPos = beginAt
                    }

                    is StingerSample -> {
                        stingerPlaying = sample
                        stingerBeginInstant = Clock.System.now()
                        stingerBeginPos = beginAt
                    }

                    is DjSample -> {
                        djPlaying = sample
                        djBeginInstant = Clock.System.now()
                        djBeginPos = beginAt
                    }
                }
            }
        }
    }

    fun stopPlayback(saveState: Boolean = true) = stopBothPlayer(saveState)

    private fun stopBothPlayer(saveState: Boolean = true) {
        currentSection = null

        // 先停止 secondary, 使 main 在之后保存状态以覆盖 secondary
        stopSecondaryPlayer(saveState)
        stopMainPlayer(saveState)
    }

    // 停止 player 的播放, 同时保存播放状态
    private fun stopMainPlayer(saveState: Boolean = true) {
        // 先保存
        if (saveState) savePlaybackState(stinger = null, dj = null)

        mainPlayer.stop()

        trackPlaying = null
        trackBeginInstant = null // 让 trackCurrentPos 返回 null
    }

    // 停止 player 的播放, 同时保存播放状态
    private fun stopSecondaryPlayer(saveState: Boolean = true) {
        // 先保存
        if (saveState) savePlaybackState(track = null)

        secondaryPlayer.stop()

        stingerPlaying = null
        stingerBeginInstant = null

        djPlaying = null
        djBeginInstant = null
    }

    private val stingerUseStartNextTrack: Boolean
        get() = when (AppSettings.radioMode) {
            RadioMode.Random -> true
            RadioMode.Seed -> true
            RadioMode.Player -> AppSettings.crossFadeEnabled
        }

    fun nextSection() {
        val current = currentSection ?: return
        val next = modeEngine?.next(current) ?: return
        beginSection(next.copy(solo = true))
    }

    // 基于音频播放位置派发 marker
    // Track only:      Track.End
    // Track + Stinger: Track.StingerStart -> Stinger.StartNextTrack + Stinger.End
    // Track + DJ:      Track.DJStart -> DJ.SampleLength
    // else:            require(isStingerAndDjMutuallyExclusive())
    fun beginSection(section: PlaySection, useTryPlay: Boolean = false) {
        if (section.solo) {
            Scheduler.cancel()
            stopBothPlayer()
        }

        // Radio 继续下一段
        fun continueNext(useTryPlay: Boolean = false) {
            modeEngine?.next(section)
                ?.let {
                    beginSection(it, useTryPlay = useTryPlay)
                }
        }

        fun scheduleOne(
            tag: String,
            triggerPosition: Duration,
            player: AudioPlayer,
            block: () -> Unit,
        ) {
            debugSnack("$tag scheduled at $triggerPosition")
            Scheduler.scheduleMarker(tag, triggerPosition, player) {
                debugSnack("$tag triggered")
                block()
            }
        }

        @Suppress("DuplicatedCode")
        when {
            section.isTrackOnly -> {
                val track = section.track!!
                if (!beginSample(track, useTryPlay = useTryPlay)) return
                currentSection = section
                modeEngine?.onSectionStarted(section)

                scheduleOne("Track.End", track.sample.end, mainPlayer) {
                    continueNext()
                }
            }

            section.isTrackAndStinger -> {
                val track = section.track!!
                val stinger = section.stinger!!
                if (!beginSample(track, useTryPlay = useTryPlay)) return
                currentSection = section
                modeEngine?.onSectionStarted(section)

                val stingerStart = track.sample.stingerStart ?: track.sample.duration
                scheduleOne("Track.StingerStart", stingerStart, mainPlayer) {
                    beginSample(stinger)

                    // 交叉淡出
                    if (stingerUseStartNextTrack) {
                        val startNextTrack = stinger.sample.startNextTrack ?: stinger.sample.end
                        scheduleOne("Stinger.StartNextTrack", startNextTrack, secondaryPlayer) {
                            continueNext()
                        }
                    }
                    scheduleOne("Stinger.End", stinger.sample.end, secondaryPlayer) {
                        // tryPlay 避免与 StartNextTrack 冲突
                        continueNext(useTryPlay = true)
                    }
                }
            }

            section.isTrackAndDj -> {
                val track = section.track!!
                val dj = section.dj!!
                if (!beginSample(track, useTryPlay = useTryPlay)) return
                currentSection = section
                modeEngine?.onSectionStarted(section)

                val djStart = track.sample.djStart ?: track.sample.duration
                scheduleOne("Track.DJStart", djStart, mainPlayer) {
                    beginSample(dj)

                    scheduleOne("DJ.SampleLength", dj.sample.end, secondaryPlayer) {
                        continueNext(useTryPlay = true)
                    }
                }
            }

            else -> {
                // 没有 Track
                // Stinger / DJ 互斥
                require(section.isStingerAndDjMutuallyExclusive) { "section.isStingerAndDjMutuallyExclusive" }

                when {
                    section.stinger != null -> {
                        val stinger = section.stinger
                        if (!beginSample(stinger, useTryPlay = useTryPlay)) return
                        currentSection = section
                        modeEngine?.onSectionStarted(section)

                        // 交叉淡出
                        if (stingerUseStartNextTrack) {
                            val startNextTrack = stinger.sample.startNextTrack ?: stinger.sample.end
                            scheduleOne("Stinger.StartNextTrack", startNextTrack, secondaryPlayer) {
                                continueNext()
                            }
                        }
                        scheduleOne("Stinger.End", stinger.sample.end, secondaryPlayer) {
                            continueNext(useTryPlay = true)
                        }
                    }

                    section.dj != null -> {
                        val dj = section.dj
                        if (!beginSample(dj, useTryPlay = useTryPlay)) return
                        currentSection = section
                        modeEngine?.onSectionStarted(section)

                        scheduleOne("DJ.SampleLength", dj.sample.end, secondaryPlayer) {
                            continueNext(useTryPlay = true)
                        }
                    }
                }
            }
        }
    }

    fun dispose() {
    }
}

internal fun Int.roll(until: Int = 100): Boolean =
    Random.nextInt(until) < this

internal fun <T> Int.roll(until: Int = 100, block: () -> T): T? =
    this.roll(until).run(block)

private fun <T> Boolean.run(block: () -> T): T? =
    if (this) block()
    else null

private fun Random.nextDuration(until: Duration): Duration {
    require(until >= Duration.ZERO) { "until must be non-negative" }
    val untilNanos = until.inWholeNanoseconds
    // 如果 until 为 0, 直接返回 0
    if (untilNanos == 0L) return Duration.ZERO
    return nextLong(0L, untilNanos).nanoseconds
}

// 对 TrackSample 提高 Track.TrackLoopStart 附近的权重
// 对 StingerSample 只随机前 1/4
// 对 DjSample 只随机前 1/2
internal fun Sample.randomBeginAt(): Duration {
    val safeDuration = (duration - 1.seconds) // 留一秒安全区
        .coerceAtLeast(Duration.ZERO)

    return when (this) {
        is TrackSample -> {
            if (trackLoopStart == null || trackLoopStart!! <= Duration.ZERO)
                Random.nextDuration(safeDuration)
            else if (safeDuration <= 1.seconds)
                Duration.ZERO
            else {
                60.roll {
                    Random.nextDuration(trackLoopStart!! * 2)
                        .coerceAtMost(safeDuration)
                } ?: run {
                    Random.nextDuration(safeDuration)
                }
            }
        }

        is StingerSample -> Random.nextDuration(safeDuration / 4)

        is DjSample -> Random.nextDuration(safeDuration / 2)
    }
}

/**
 * @throws [FileNotFoundException] 如果可用的扩展名都匹配失败
 */
internal fun RadioStation.resolvePath(sample: Sample): String? {
    val source = this.getSource() ?: return null
    val relPath = pathFor(sample) ?: return null

    val base = joinPath(source.audioFolderPath, relPath.toString())
    val primaryExt = source.audioExtension
    var path = "$base.$primaryExt"
    if (fileExists(path)) return path

    for (ext in SUPPORTED_FORMATS) {
        if (ext == primaryExt) continue
        path = "$base.$ext"
        if (fileExists(path)) return path
    }

    throw FileNotFoundException("$base.*")
}
