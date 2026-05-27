package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.AppSettings.getSource
import io.github.miuzarte.fhradio.model.*
import kotlinx.coroutines.*
import okio.FileNotFoundException
import okio.Path.Companion.toPath
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

    // 通过显示置 null 以跳过指定 Sample 的保存
    private fun savePlaybackState(
        track: TrackSample? = trackPlaying,
        stinger: StingerSample? = stingerPlaying,
        dj: DjSample? = djPlaying,
    ) {
        selectedStation ?: return
        track?.let {
            AppSettings.playbackState = PlaybackState(it.soundName, trackCurrentPos!!, SampleType.Track)
            return
        }
        stinger?.let {
            AppSettings.playbackState = PlaybackState(it.soundName, stingerCurrentPos!!, SampleType.Track)
            return
        }
        dj?.let {
            AppSettings.playbackState = PlaybackState(it.soundName, djCurrentPos!!, SampleType.DJ)
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
            return
        }

        // open/switch station
        selectedStation = station
        modeEngine = selectEngine(station)
        // 直接 beginSection(resume) 即可, 不需要 reschedule

        if (play) {
            modeEngine
                ?.resume(AppSettings.playbackState)
                ?.let { beginSection(it) }
        }
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
        get() = trackBeginInstant?.let { trackBeginPos + (Clock.System.now() - it) }

    var stingerPlaying: StingerSample? by mutableStateOf(null)
        private set
    internal var stingerBeginInstant: Instant? = null
    internal var stingerBeginPos: Duration = Duration.ZERO
    internal val stingerCurrentPos: Duration?
        get() = stingerBeginInstant?.let { stingerBeginPos + (Clock.System.now() - it) }

    var djPlaying: DjSample? by mutableStateOf(null)
        private set
    internal var djBeginInstant: Instant? = null
    internal var djBeginPos: Duration = Duration.ZERO
    internal val djCurrentPos: Duration?
        get() = djBeginInstant?.let { djBeginPos + (Clock.System.now() - it) }

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

        // 先停止 secondary, 使 main 之后保存状态以覆盖 secondary
        stopSecondaryPlayer(saveState)
        stopMainPlayer(saveState)
    }

    // 停止 player 的播放, 同时保存播放状态
    private fun stopMainPlayer(saveState: Boolean = true) {
        mainPlayer.stop()

        if (saveState) savePlaybackState(stinger = null, dj = null)

        trackPlaying = null
        trackBeginInstant = null // 让 trackCurrentPos 返回 null
    }

    // 停止 player 的播放, 同时保存播放状态
    private fun stopSecondaryPlayer(saveState: Boolean = true) {
        secondaryPlayer.stop()

        if (saveState) savePlaybackState(track = null)

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

    // 整个 section 一次性派发所有 marker
    // Track only:      Track.End
    // Track + Stinger: Track.StingerStart, Stinger.StartNextTrack
    // Track + DJ:      Track.DJStart, DJ.SampleLength
    // else:            require(isStingerAndDjMutuallyExclusive())
    fun beginSection(section: PlaySection, useTryPlay: Boolean = false) {
        if (section.solo) {
            Scheduler.cancel()
            stopBothPlayer()
        }
        currentSection = section
        modeEngine?.onSectionStarted(section)

        // Radio 继续下一段
        fun continueNext(useTryPlay: Boolean = false) {
            modeEngine?.next(section)
                ?.let {
                    beginSection(it, useTryPlay = useTryPlay)
                }
                ?: run {
                    trackBeginInstant = null
                    stingerBeginInstant = null
                    djBeginInstant = null
                    return
                }
        }

        fun scheduleOne(
            tag: String,
            delay: Duration,
            block: () -> Unit,
        ) {
            debugSnack("$tag scheduled in $delay")
            Scheduler.scheduleMarker(tag, delay) {
                debugSnack("$tag triggered")
                block()
            }
        }

        when {
            section.isTrackOnly -> {
                val track = section.track!!
                beginSample(track, useTryPlay = useTryPlay)

                val tag = "Track.End"
                val delay = (-track.beginAt) + track.sample.end
                scheduleOne(tag, delay) {
                    continueNext()
                }
            }

            section.isTrackAndStinger -> {
                val track = section.track!!
                val stinger = section.stinger!!
                beginSample(track, useTryPlay = useTryPlay)

                val stingerStart = track.sample.stingerStart ?: track.sample.duration
                val stingerStartDelay = (-track.beginAt) + stingerStart
                scheduleOne("Track.StingerStart", stingerStartDelay) {
                    beginSample(stinger)
                }

                // 交叉淡出
                if (stingerUseStartNextTrack) {
                    val startNextTrack = stinger.sample.startNextTrack ?: stinger.sample.end
                    val startNextTrackDelay = stingerStartDelay +
                            ((-stinger.beginAt) + startNextTrack)
                    scheduleOne("Stinger.StartNextTrack", startNextTrackDelay) {
                        // 只切歌，不清 stingerPlaying
                        continueNext()
                    }
                }

                val endDelay = stingerStartDelay +
                        ((-stinger.beginAt) + stinger.sample.end)
                scheduleOne("Stinger.End", endDelay) {
                    // tryPlay 避免与 StartNextTrack 冲突
                    continueNext(useTryPlay = true)
                }
            }

            section.isTrackAndDj -> {
                val track = section.track!!
                val dj = section.dj!!
                beginSample(track, useTryPlay = useTryPlay)

                val djStart = track.sample.djStart ?: track.sample.duration
                val djStartDelay = (-track.beginAt) + djStart
                scheduleOne("Track.DJStart", djStartDelay) {
                    beginSample(dj)
                }

                val endDelay = djStartDelay + ((-dj.beginAt) + dj.sample.end)
                scheduleOne("DJ.SampleLength", endDelay) {
                    continueNext(useTryPlay = true)
                }
            }

            else -> {
                // 没有 Track
                // Stinger / DJ 互斥
                require(section.isStingerAndDjMutuallyExclusive) { "section.isStingerAndDjMutuallyExclusive" }

                when {
                    section.stinger != null -> {
                        val stinger = section.stinger
                        beginSample(stinger, useTryPlay = useTryPlay)

                        // 交叉淡出
                        if (stingerUseStartNextTrack) {
                            val startNextTrack = stinger.sample.startNextTrack ?: stinger.sample.end
                            val startNextTrackDelay = (-stinger.beginAt) + startNextTrack
                            scheduleOne("Stinger.StartNextTrack", startNextTrackDelay) {
                                continueNext()
                            }
                        }

                        val endDelay = (-stinger.beginAt) + stinger.sample.end
                        scheduleOne("Stinger.End", endDelay) {
                            continueNext(useTryPlay = true)
                        }
                    }

                    section.dj != null -> {
                        val dj = section.dj
                        beginSample(dj, useTryPlay = useTryPlay)

                        val endDelay = (-dj.beginAt) + (dj.sample.end)
                        scheduleOne("DJ.SampleLength", endDelay) {
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
    // 如果 until 为 0，直接返回 0
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

    val base = (source.audioFolderPath.toPath() / relPath).toString()
    val primaryExt = source.audioExtension
    var path = "$base.$primaryExt"
    if (fileExists(path)) return path

    for (ext in listOf("wav", "flac", "mp3", "opus")) {
        if (ext == primaryExt) continue
        path = "$base.$ext"
        if (fileExists(path)) return path
    }

    throw FileNotFoundException("$base.*")
}
