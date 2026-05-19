package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.*
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import okio.Path.Companion.toPath

private fun debugDo(block: () -> Unit) {
    if (BuildKonfig.DEBUG) block()
}

object Radio {
    private val mainPlayer
        get() = AppRuntime.mainPlayer
    private val secondaryPlayer
        get() = AppRuntime.secondaryPlayer
    private val settings
        get() = AppSettings.radioSettings
    private val crossLists
        get() = AppSettings.crossLists
    private val patternNodes
        get() = AppSettings.loadPatternNodes()

    var selectedStation: RadioStation? by mutableStateOf(null)

    private val selectedTracks
        get() = selectedStation?.tracks
    private val selectedStingers
        get() = selectedStation?.stingers
    private val selectedDj
        get() = selectedStation?.djSamples

    private fun sampleList(type: SampleType): List<Sample>? =
        when (type) {
            SampleType.Track -> selectedTracks
            SampleType.Stinger -> selectedStingers
            SampleType.DJ -> selectedDj
        }

    var currentTrack: TrackSample? by mutableStateOf(null)
    lateinit var lastTrack: TrackSample
    var currentStinger: StingerSample? by mutableStateOf(null)
    lateinit var lastStinger: StingerSample
    var currentDj: DjSample? by mutableStateOf(null)
    lateinit var lastDj: DjSample

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun openStation(
        station: RadioStation,
        play: Boolean = true, // 切换到电台后直接开始播放
    ) {
        closeStation()
        selectedStation = station
        AppSettings.saveLastStation(station)
        if (play) resumeOrRandom()
    }

    fun closeStation() {
        stopPlayback()
        selectedStation = null
    }

    // --- 调度 ---

    data class ScheduledInfo(
        val tag: String,
        val targetPos: Int,
        val timeMs: Long,
        val remainingMs: Long,
        val fireAtMs: Long,
    )

    private data class TaggedJob(val job: Job, val tag: String)

    private data class DebugMeta(
        val targetPos: Int,
        val timeMs: Long,
        val delayMs: Long,
        val scheduledAtMs: Long,
    )

    private val scheduledJobs = mutableListOf<Job>()
    private val taggedJobs = mutableListOf<TaggedJob>()
    private val debugMetas = mutableMapOf<String, DebugMeta>()

    var debugScheduledMarkers: List<ScheduledInfo> by mutableStateOf(emptyList())
        private set

    private fun syncDebugMarkers() {
        val nowMs = currentTimeMillis()
        debugScheduledMarkers = debugMetas.map { (tag, meta) ->
            val elapsed = nowMs - meta.scheduledAtMs
            val remainingMs = (meta.delayMs - elapsed).coerceAtLeast(0)
            ScheduledInfo(tag, meta.targetPos, meta.timeMs, remainingMs, meta.scheduledAtMs + meta.delayMs)
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
    }

    private fun cancelAllScheduled() {
        scheduledJobs.forEach { it.cancel() }
        scheduledJobs.clear()
        taggedJobs.clear()
        debugMetas.clear()
        syncDebugMarkers()
    }

    // --- Marker 派发 ---

    // 仅负责派发协程, 按 delayMs 延迟执行 block
    private fun schedule(
        tag: String,
        delayMs: Long,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (delayMs <= 0) {
            scope.launch(Dispatchers.Default, block = block)
            return
        }
        val job = scope.launch(Dispatchers.Default) {
            delay(delayMs.milliseconds)
            block()
        }
        scheduledJobs.add(job)
        taggedJobs.add(TaggedJob(job, tag))
    }

    private fun scheduleMarkers(sample: Sample, beginMs: Long = 0) {
        // 从 [sample] 的 targetPos + beginMs 自动计算延迟, 派发并维护 debug 信息
        fun scheduleMarker(
            tag: String,
            sample: Sample,
            targetPos: Int,
            beginMs: Long,
            block: suspend CoroutineScope.() -> Unit,
        ) {
            val fullMs = targetPos * 1000L / sample.sampleRate
            val delayMs = fullMs - beginMs
            if (delayMs <= 0) {
                schedule(tag, 0, block)
                return
            }
            val scheduledAtMs = currentTimeMillis()
            schedule(tag, delayMs, block)

            debugMetas[tag] = DebugMeta(targetPos, fullMs, delayMs, scheduledAtMs)
            syncDebugMarkers()
        }

        when (sample) {
            is TrackSample -> {
                when (settings.radioMode) {
                    RadioMode.Random -> {
                        if (sample.stingerStart > 0 && Random.nextInt(100) < settings.stingerChance)
                            scheduleMarker("Track.StingerStart", sample, sample.stingerStart, beginMs) {
                                debugDo { AppRuntime.snackbar("Track.StingerStart @ ${sample.stingerStart}") }
                                onStingerStart()
                            }

                        if (sample.djStart > 0 && Random.nextInt(100) < settings.djChance)
                            scheduleMarker("Track.DJStart", sample, sample.djStart, beginMs) {
                                debugDo { AppRuntime.snackbar("Track.DJStart @ ${sample.djStart}") }
                                onDjStart()
                            }
                    }

                    RadioMode.Seed -> {
                        TODO("未实现")
                    }

                    RadioMode.Player -> {

                    }
                }

                val tag = if (sample.end > 0) "Track.End" else "Track.SampleLength"
                val targetPos = if (sample.end > 0) sample.end else sample.sampleLength
                scheduleMarker(tag, sample, targetPos, beginMs) {
                    debugDo { AppRuntime.snackbar("$tag @ $targetPos") }
                    onTrackEnd()
                }
            }

            is StingerSample -> {
                when (settings.radioMode) {
                    RadioMode.Random -> {
                        if (sample.startNextTrack > 0)
                            scheduleMarker("Stinger.StartNextTrack", sample, sample.startNextTrack, beginMs) {
                                debugDo { AppRuntime.snackbar("Stinger.StartNextTrack @ ${sample.startNextTrack}") }
                                onStingerNextTrack()
                            }
                    }

                    RadioMode.Seed -> {
                        TODO("未实现")
                    }

                    RadioMode.Player -> {
                        if (sample.startNextTrack > 0 && settings.crossFadeEnabled)
                            scheduleMarker("Stinger.StartNextTrack", sample, sample.startNextTrack, beginMs) {
                                debugDo { AppRuntime.snackbar("Stinger.StartNextTrack @ ${sample.startNextTrack}") }
                                onStingerNextTrack()
                            }
                    }
                }

                val tag = if (sample.end > 0) "Stinger.End" else "Stinger.SampleLength"
                val targetPos = if (sample.end > 0) sample.end else sample.sampleLength
                scheduleMarker(tag, sample, targetPos, beginMs) {
                    debugDo { AppRuntime.snackbar("$tag @ $targetPos") }
                    onStingerEnd()
                }
            }

            is DjSample -> {
                when (settings.radioMode) {
                    RadioMode.Random -> {}
                    RadioMode.Seed -> {
                        TODO("未实现")
                    }

                    RadioMode.Player -> {}
                }

                scheduleMarker("DJ.SampleLength", sample, sample.sampleLength, beginMs) {
                    debugDo { AppRuntime.snackbar("DJ.SampleLength @ ${sample.sampleLength}") }
                    onDjEnd()
                }
            }
        }

    }

    // --- Marker 回调 ---

    private fun stingerPlayerIsBusy(): Boolean {
        val s = secondaryPlayer.getState()
        return s.status == PlaybackStatus.Playing ||
                s.status == PlaybackStatus.Buffering ||
                s.status == PlaybackStatus.Opening
    }

    private fun onTrackStart() {
    }

    private fun onTrackEnd() {
        currentTrack = null
        if (currentStinger == null && currentDj == null)
            advance()
    }

    private fun onStingerStart() {
        val stinger = selectedStingers
            ?.filter { it.resolvePath() != null }
            ?.randomOrNull()
            ?: return
        if (beginSample(stinger, solo = false, useTryPlay = true)) {
            cancelScheduledByTag("DJStart")
            cancelScheduledByTag("TrackEnd")
        }
    }

    private fun onStingerNextTrack() {
        advance()
    }

    private fun onStingerEnd() {
        currentStinger = null
        val track = nextSample(SampleType.Track)
        if (track != null) beginSample(track, solo = false, useTryPlay = true)
    }

    private fun onDjStart() {
        if (currentTrack == null) return
        if (stingerPlayerIsBusy()) return
        val dj = selectedDj
            ?.filter { it.resolvePath() != null }
            ?.randomOrNull()
            ?: return
        beginSample(dj, solo = false, useTryPlay = true)
    }

    private fun onDjEnd() {
        currentDj = null
        advance()
    }

    // --- 播放控制 ---

    private var patternIndex = 0

    private var lastPlayedSample: Sample? = null
    private var periodicSaveJob: Job? = null

    private var trackStartedAtMs = 0L // sample 播放时系统时间点
    private var trackBeginMs = 0L // sample 续播点

    private var stingerStartedAtMs = 0L
    private var stingerBeginMs = 0L

    private var djStartedAtMs = 0L
    private var djBeginMs = 0L

    private var lastRescheduleMs = 0L

    // --- 手动播放 ---

    // Tab 双击随机播放, 低优先级
    fun playRandomFromList(type: SampleType) {
        selectedStation ?: return
        if (currentTrack != null || currentStinger != null || currentDj != null) return
        val list = sampleList(type) ?: return

        val sample = list.filter { it.resolvePath() != null }.randomOrNull()
        if (sample != null) playSample(sample)
        else AppRuntime.snackbar("无可用曲目")
    }

    // 曲目页手动播放, 抢占所有通道
    fun playSampleFromList(sample: Sample) {

    }

    fun playSample(sample: Sample): Boolean {
        selectedStation ?: return false

        lastPlayedSample = sample
        resetPatternIndex(sample.type)

        return beginSample(sample)
    }

    fun resumeOrRandom() {
        val pbState = SettingsStore.loadPlaybackState()
        val name = pbState.soundName
        val posMs = pbState.positionMs
        val sampleType = when (pbState.type) {
            "Track" -> SampleType.Track
            "Stinger" -> SampleType.Stinger
            "DJ" -> SampleType.DJ
            else -> return
        }

        when (settings.radioMode) {
            RadioMode.Random -> {
                playRandom(sampleType)
            }

            RadioMode.Seed -> {
                TODO("种子控制")
            }

            RadioMode.Player -> {
                if (name != null) {
                    val resumedOk = when (sampleType) {
                        SampleType.Track -> selectedTracks?.find { it.soundName == name }
                            ?.let { sample -> beginSample(sample, posMs) }

                        SampleType.Stinger -> selectedStingers?.find { it.soundName == name }
                            ?.let { sample -> beginSample(sample, posMs) }

                        SampleType.DJ -> selectedDj?.find { it.soundName == name }
                            ?.let { sample -> beginSample(sample, posMs) }
                    }
                    if (resumedOk == true) return
                }
                // 无续播信息: Order 从第一首, Shuffle 随机
                when (settings.playMode) {
                    PlayMode.Shuffle -> {
                        playNext(sampleType)
                    }

                    PlayMode.Order -> {
                        val list = selectedTracks ?: return
                        if (list.isNotEmpty()) beginSample(list.first())
                    }
                }
            }
        }
    }

    private fun playNext(type: SampleType, beginMs: Long = 0) {
        val sample = nextSample(type) ?: return
        beginSample(sample, beginMs, solo = false)
    }

    private fun playRandom(type: SampleType) {
        val sample = nextSample(type) ?: return
        beginSample(sample, sample.randomBeginMs(), solo = false)
    }

    fun beginSample(
        sample: Sample,
        beginMs: Long = 0,
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

                trackStartedAtMs = currentTimeMillis()
                trackBeginMs = beginMs
                if (useTryPlay) mainPlayer.tryPlay(path, beginMs)
                else mainPlayer.play(path, beginMs)
            }

            is StingerSample -> {
                currentStinger = sample
                lastStinger = sample

                currentTrack = null
                currentDj = null

                stingerStartedAtMs = currentTimeMillis()
                stingerBeginMs = beginMs
                if (useTryPlay) secondaryPlayer.tryPlay(path, beginMs)
                else secondaryPlayer.play(path, beginMs)
            }

            is DjSample -> {
                currentDj = sample
                lastDj = sample

                currentTrack = null
                currentStinger = null

                djStartedAtMs = currentTimeMillis()
                djBeginMs = beginMs
                if (useTryPlay) secondaryPlayer.tryPlay(path, beginMs)
                else secondaryPlayer.play(path, beginMs)
            }
        }

        debugDo {
            val totalSec = beginMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val timeStr = """${if (min > 0) "${min}m" else ""}${sec}s"""
            val samples = beginMs * sample.sampleRate / 1000
            AppRuntime.snackbar("切入: $timeStr (${samples})")
        }

        scheduleMarkers(sample, beginMs)
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
                        val posMs = currentTrackPosMs()
                        SettingsStore.savePlaybackState(t.soundName, posMs, "Track")
                    }

                    currentStinger != null -> {
                        val s = currentStinger!!
                        val posMs = currentStingerPosMs()
                        SettingsStore.savePlaybackState(s.soundName, posMs, "Stinger")
                    }

                    currentDj != null -> {
                        val d = currentDj!!
                        val posMs = currentDjPosMs()
                        SettingsStore.savePlaybackState(d.soundName, posMs, "DJ")
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

    private fun stopMain() {
        mainPlayer.stop()
        cancelScheduledByTag("Track.StingerStart")
        cancelScheduledByTag("Track.DJStart")
        cancelScheduledByTag("Track.End")
        cancelScheduledByTag("Track.SampleLength")
    }

    private fun stopSecondary() {
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

    // --- Pattern 推进 ---

    private fun resetPatternIndex(type: SampleType) {
        val nodes = effectivePattern()
        val found = nodes.indexOfFirst { it.type == type }
        patternIndex = if (found >= 0) (found + 1) % nodes.size else 0
    }

    fun resetPatternState() {
        patternIndex = 0
    }

    internal fun currentTrackPosMs() =
        if (currentTrack != null && trackStartedAtMs > 0)
            trackBeginMs + (currentTimeMillis() - trackStartedAtMs)
        else 0L

    internal fun currentStingerPosMs() =
        if (currentStinger != null && stingerStartedAtMs > 0)
            stingerBeginMs + (currentTimeMillis() - stingerStartedAtMs)
        else 0L

    internal fun currentDjPosMs() =
        if (currentDj != null && djStartedAtMs > 0)
            djBeginMs + (currentTimeMillis() - djStartedAtMs)
        else 0L

    // 更新设置后重新派发调度
    // 300ms 消抖
    fun reschedule() {
        val now = currentTimeMillis()
        if (now - lastRescheduleMs < 300) return
        lastRescheduleMs = now
        cancelAllScheduled()

        currentTrack?.let { track ->
            if (trackStartedAtMs > 0) {
                scheduleMarkers(track, currentTrackPosMs())
            }
        }

        currentStinger?.let { stinger ->
            if (stingerStartedAtMs > 0) {
                scheduleMarkers(stinger, currentStingerPosMs())
            }
        }

        currentDj?.let { dj ->
            if (djStartedAtMs > 0) {
                scheduleMarkers(dj, currentDjPosMs())
            }
        }
    }

    private fun effectivePattern(): List<PatternNode> {
        if (settings.patternEnabled)
            patternNodes.takeIf { it.isNotEmpty() }
                ?.let { return it }

        return crossLists.map { PatternNode(it) }
    }

    private fun advance() {
        when (settings.radioMode) {
            RadioMode.Random -> {
                playNext(SampleType.Track)
            }

            RadioMode.Seed -> {
                TODO()
            }

            RadioMode.Player -> {
                if (settings.patternEnabled) {
                    patternNodes.takeIf { it.isNotEmpty() }
                        ?.let { advanceByPattern(it) }
                        ?: playNext(SampleType.Track)
                } else {
                    crossLists.takeIf { it.isNotEmpty() }
                        ?.let { advanceByCrossList(it) }
                        ?: playNext(SampleType.Track)
                }
            }
        }
    }

    private fun advanceByPattern(nodes: List<PatternNode>) {
        selectedStation ?: return

        var attempt = 0
        while (attempt < nodes.size) {
            patternIndex %= nodes.size
            val node = nodes[patternIndex]
            patternIndex++

            if (node.probability < 100 && Random.nextInt(100) >= node.probability) {
                attempt++
                continue
            }

            if (playNode(node)) return
            attempt++
        }

        playNext(SampleType.Track)
    }

    private fun advanceByCrossList(crossLists: List<SampleType>) {
        selectedStation ?: return

        val lastSample = lastPlayedSample
        val orderMode = settings.playMode == PlayMode.Order

        // 在同类型列表内继续
        if (orderMode && lastSample != null) {
            val sample = nextSample(lastSample.type)
            if (sample != null) {
                beginSample(sample)
                return
            }
        }

        // 切换到下一个跨列表类型
        val currentIdx = crossLists.indexOf(lastSample?.type ?: crossLists.first())
        val nextType =
            if (currentIdx >= 0) crossLists[(currentIdx + 1) % crossLists.size]
            else crossLists.first()
        val list = sampleList(nextType)?.filter { it.resolvePath() != null }
        if (list.isNullOrEmpty()) {
            playNext(nextType)
            return
        }
        val sample = nextSample(nextType)
        if (sample != null) beginSample(sample)
        else playNext(nextType)
    }

    private fun randomSample(
        type: SampleType,
        exclude: Set<Sample> = emptySet(),
    ): Sample? {
        val list = sampleList(type) ?: return null

        if (list.all { it in exclude }) return null
        var sample: Sample
        do {
            sample = list[Random.nextInt(list.size)]
        } while (sample in exclude)
        return sample
    }

    // Order 基于 lastPlayedSample 索引 + step 推进
    // Shuffle 随机
    private fun nextSample(
        type: SampleType,
        step: Int = 1,
        exclude: Set<Sample> = emptySet(), // TODO: 用于伪随机排除随机到同一首
    ): Sample? {
        val list = sampleList(type) ?: return null

        val settingsFakeRandom = true // TODO: add option to settings
        val fakeRandom = settingsFakeRandom || settings.radioMode == RadioMode.Random

        return when (settings.radioMode) {
            RadioMode.Random -> {
                randomSample(type, exclude)
            }

            RadioMode.Seed -> {
                TODO("未实现")
            }

            RadioMode.Player -> {
                when (settings.playMode) {
                    PlayMode.Shuffle -> {
                        if (fakeRandom) randomSample(type, exclude)
                        else randomSample(type)
                    }

                    PlayMode.Order -> {
                        val idx = lastPlayedSample
                            ?.takeIf { it.type == type }
                            ?.let { list.indexOf(it) }
                            ?: -1
                        if (idx >= 0) list[(idx + step) % list.size]
                        else list.first()
                    }
                }
            }
        }

    }

    // --- Node 播放 (Pattern / CrossList) ---

    private fun playNode(node: PatternNode): Boolean {
        selectedStation ?: return false
        val type = node.type
        val list = sampleList(type)?.filter { it.resolvePath() != null }
        if (list.isNullOrEmpty()) return false

        val lastOfType = when (type) {
            SampleType.Track -> lastTrack
            SampleType.Stinger -> lastStinger
            SampleType.DJ -> lastDj
        }
        val orderMode = settings.playMode == PlayMode.Order
        val sample = if (orderMode) {
            val idx = lastOfType?.let { list.indexOf(it) }?.takeIf { it >= 0 } ?: -1
            if (idx >= 0) list[(idx + node.step) % list.size] else list.first()
        } else {
            list[Random.nextInt(list.size)]
        }
        stopMain()
        stopSecondary()
        return beginSample(sample, solo = false)
    }

    // --- 工具 ---

    fun Sample.resolvePath(): String? {
        val station = selectedStation ?: return null
        val root = AppSettings.findSourcePath(station) ?: return null
        val (sub, name) = when (this) {
            is TrackSample -> {
                "Track" to this.soundName
            }

            is DjSample -> {
                val idx = sampleList(this.type)?.indexOf(this) ?: return null
                "DJ" to if (idx < 0) return null else "sound_$idx"
            }

            is StingerSample -> {
                val idx = sampleList(this.type)?.indexOf(this) ?: return null
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

    private fun Sample.randomBeginMs(): Long {
        when (this) {
            is TrackSample -> {
                if (this.durationMs <= 1000) return 0

                val loopStart = this.trackLoopStart
                if (loopStart <= 0) return Random.nextLong(this.durationMs)

                val loopStartMs = loopStart * 1000L / this.sampleRate
                return if (Random.nextInt(100) < 40) {
                    val offset = Random.nextLong(-loopStartMs, loopStartMs + 1)
                    (loopStartMs + offset).coerceIn(0, this.durationMs - 1000)
                } else {
                    Random.nextLong(this.durationMs)
                }
            }

            is StingerSample -> {
                return Random.nextLong(0, durationMs / 3)
            }

            is DjSample -> {
                return Random.nextLong(0, durationMs / 2)
            }
        }
    }

    fun dispose() {
        cancelAllScheduled()
    }
}
