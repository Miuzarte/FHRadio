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
import kotlin.time.Clock
import kotlin.time.Duration
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
    private val Sample.player
        get() = when (this) {
            is TrackSample -> mainPlayer
            is StingerSample -> secondaryPlayer
            is DjSample -> secondaryPlayer
        }

    var selectedStation: RadioStation? by mutableStateOf(null)
        private set
    private var modeEngine: RadioModeEngineV2? = null

    fun getPlayList() = modeEngine?.getPlayList()

    // 通过显式置 null 以跳过指定 Sample 的保存
    private fun savePlaybackState(
        track: TrackSample? = trackSlot.playing,
        stinger: StingerSample? = stingerSlot.playing,
        dj: DjSample? = djSlot.playing,
    ) {
        selectedStation ?: return
        track?.let {
            // debugSnack("saving track ${it.soundName} @ ${trackSlot.currentPos}")
            val position = trackSlot.currentPos ?: Duration.ZERO
            AppSettings.playbackState = PlaybackState(it.soundName, position, SampleType.Track)
            return
        }
        stinger?.let {
            // debugSnack("saving stinger ${it.soundName} @ ${stingerSlot.currentPos}")
            val position = stingerSlot.currentPos ?: Duration.ZERO
            AppSettings.playbackState = PlaybackState(it.soundName, position, SampleType.Stinger)
            return
        }
        dj?.let {
            // debugSnack("saving dj ${it.soundName} @ ${djSlot.currentPos}")
            val position = djSlot.currentPos ?: Duration.ZERO
            AppSettings.playbackState = PlaybackState(it.soundName, position, SampleType.DJ)
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

    private val stingerUseStartNextTrack: Boolean
        get() = when (AppSettings.radioMode) {
            RadioMode.Random -> true
            RadioMode.Seed -> true
            RadioMode.Player -> AppSettings.crossFadeEnabled
        }

    // --- Playback control ---

    private var currentSection: PlaySection? = null

    class PlaySlot<T: Sample>(
        val playing: T? = null, // 当前播放
        val beginInstant: Instant? = null, // 播放开始时间点
        val beginPos: Duration = Duration.ZERO, // 播放切入点
    ) {
        val player: AudioPlayer?
            get() = playing?.player

        // 播放当前进度(Player)
        // 用于 marker 触发
        val currentPos: Duration?
            get() = playing?.player?.state?.position

        // 播放当前进度(计算)
        // 实时性高用于 UI
        val displayPos: Duration?
            get() {
                playing ?: return null
                beginInstant ?: return null
                val raw = beginPos + (Clock.System.now() - beginInstant)
                return raw.coerceAtMost(playing.duration)
            }
    }

    var trackSlot by mutableStateOf(PlaySlot<TrackSample>())
        private set
    var stingerSlot by mutableStateOf(PlaySlot<StingerSample>())
        private set
    var djSlot by mutableStateOf(PlaySlot<DjSample>())
        private set

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
        val path = runCatching { selectedStation?.resolvePath(sample) }
            .onFailure { e ->
                AppRuntime.snackbar("failed to resolve path of ${sample.soundName}: ${e.message ?: e.toString()}")
            }
            .getOrNull()
            ?: return false
        val beginAt = playItem.beginAt

        return if (useTryPlay) {
            player.tryPlay(path, beginAt)
        } else {
            player.play(path, beginAt)
            true
        }.also {
            if (it) {
                val now = Clock.System.now()
                when (sample) {
                    is TrackSample -> trackSlot =
                        PlaySlot(playing = sample, beginInstant = now, beginPos = beginAt)

                    is StingerSample -> stingerSlot =
                        PlaySlot(playing = sample, beginInstant = now, beginPos = beginAt)

                    is DjSample -> djSlot =
                        PlaySlot(playing = sample, beginInstant = now, beginPos = beginAt)
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

        trackSlot = PlaySlot()
    }

    // 停止 player 的播放, 同时保存播放状态
    private fun stopSecondaryPlayer(saveState: Boolean = true) {
        // 先保存
        if (saveState) savePlaybackState(track = null)

        secondaryPlayer.stop()

        stingerSlot = PlaySlot()
        djSlot = PlaySlot()
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

    throw FileNotFoundException("$base.$SUPPORTED_FORMATS")
}
