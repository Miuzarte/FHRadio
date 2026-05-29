package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SeedEngine(
    station: RadioStation,
    private val seed: Long,
): RadioModeEngine(station) {

    companion object {
        private const val STINGER_PROB = 75 // 我也不知道具体是多少, 暂定 75/25
        private const val DJ_PROB = 25
        private const val PLAYLIST_CYCLES = 100
        val DJ_ALLOWED_EVENTS = RadioSettings.defaultDjGameEvents
        private val excludedTrackSuffixes = RadioSettings.defaults.excludedTrackSuffixes
    }

    private data class SectionTiming(
        val sectionIndex: Long,
        val startTimeMs: Long,
        val durationMs: Long,
        val track: TrackSample,
        val stinger: StingerSample?,
        val dj: DjSample?,
    )

    private val trackPool: List<TrackSample> = station.track.filter { t ->
        excludedTrackSuffixes.none { t.soundName.endsWith(it) }
    }
    private val stingerPool: List<StingerSample> = station.stinger
    private val djPool: List<DjSample> =
        if (DJ_ALLOWED_EVENTS.isEmpty()) station.dj
        else station.dj.filter { it.gameEvent in DJ_ALLOWED_EVENTS }
    private val sections: List<SectionTiming> = if (trackPool.isEmpty()) emptyList() else generatePlaylist()
    private val totalDurationMs: Long = sections.lastOrNull()?.let { it.startTimeMs + it.durationMs } ?: 0L

    val isEmpty: Boolean get() = sections.isEmpty()

    private var currentSectionIndex: Int = 0

    override fun next(current: PlaySection?): PlaySection {
        if (isEmpty) return fallbackSection()
        val prev = currentSectionIndex
        currentSectionIndex = (currentSectionIndex + 1) % sections.size
        val t = sections[currentSectionIndex]
        debugSnack("SeedEngine.next: $prev -> $currentSectionIndex, track=${t.track.soundName}, stinger=${t.stinger?.soundName}, dj=${t.dj?.soundName}")
        return buildPlaySection(t)
    }

    override fun resume(playbackState: PlaybackState?): PlaySection? {
        if (isEmpty) return null
        currentSectionIndex = findSectionByTime()
        val sectionTiming = sections[currentSectionIndex]
        debugSnack("SeedEngine.resume: index=$currentSectionIndex, time=${sectionTiming.startTimeMs}ms, track=${sectionTiming.track.soundName}, stinger=${sectionTiming.stinger?.soundName}, dj=${sectionTiming.dj?.soundName}")
        val wallClockMs = Clock.System.now().toEpochMilliseconds()
        val effectiveMs = wallClockMs % totalDurationMs
        val offsetMs = (effectiveMs - sectionTiming.startTimeMs).coerceAtLeast(0)

        val trackBegin = offsetMs.milliseconds

        val stingerBegin = sectionTiming.stinger?.let {
            val stingerStartMs = sectionTiming.startTimeMs + sectionTiming.track.stingerStart!!.inWholeMilliseconds
            val stingerOffset = effectiveMs - stingerStartMs
            if (stingerOffset > 0) stingerOffset.milliseconds else Duration.ZERO
        }

        val djBegin = sectionTiming.dj?.let {
            val djStartMs = sectionTiming.startTimeMs + sectionTiming.track.djStart!!.inWholeMilliseconds
            val djOffset = effectiveMs - djStartMs
            if (djOffset > 0) djOffset.milliseconds else Duration.ZERO
        }

        return PlaySection(
            track = PlayItem.Track(sectionTiming.track, trackBegin),
            stinger = sectionTiming.stinger?.let { PlayItem.Stinger(it, stingerBegin!!) },
            dj = sectionTiming.dj?.let { PlayItem.Dj(it, djBegin!!) },
        )
    }

    override fun getPlayList(): Pair<List<PlaySection>, Int?>? {
        if (isEmpty) return null
        val list = sections.drop(currentSectionIndex).map { buildPlaySection(it) }
        return list to 0
    }

    // --- private ---

    private fun generatePlaylist(): List<SectionTiming> {
        val result = mutableListOf<SectionTiming>()
        var currentTime = 0L
        val stingerRecent = ArrayDeque<StingerSample>(4)
        val djRecent = ArrayDeque<DjSample>(4)

        for (cycle in 0 until PLAYLIST_CYCLES) {
            val cycleRng = Random(hash64(seed, cycle.toLong()))
            val shuffled = shuffle(trackPool, cycleRng)

            for ((trackIdx, track) in shuffled.withIndex()) {
                val sectionIndex = (cycle * trackPool.size + trackIdx).toLong()
                val sectionRng = Random(hash64(seed, sectionIndex))

                val totalProb = STINGER_PROB + DJ_PROB
                val (stinger, dj) = when {
                    totalProb == 0 -> null to null
                    DJ_PROB == 0 -> {
                        if (sectionRng.nextInt(100) < STINGER_PROB)
                            pickRecent(stingerPool, stingerRecent, sectionRng) to null
                        else null to null
                    }

                    STINGER_PROB == 0 -> {
                        if (sectionRng.nextInt(100) < DJ_PROB)
                            null to pickRecent(djPool, djRecent, sectionRng)
                        else null to null
                    }

                    else -> {
                        if (sectionRng.nextInt(totalProb) < STINGER_PROB)
                            pickRecent(stingerPool, stingerRecent, sectionRng) to null
                        else
                            null to pickRecent(djPool, djRecent, sectionRng)
                    }
                }

                val durationMs = if (stinger != null)
                    track.stingerStart!!.inWholeMilliseconds + stinger.startNextTrack!!.inWholeMilliseconds
                else
                    track.end.inWholeMilliseconds

                result.add(
                    SectionTiming(
                        sectionIndex = sectionIndex,
                        startTimeMs = currentTime,
                        durationMs = durationMs,
                        track = track,
                        stinger = stinger,
                        dj = dj,
                    ),
                )
                currentTime += durationMs
            }
        }
        return result
    }

    private fun findSectionByTime(): Int {
        val wallClockMs = Clock.System.now().toEpochMilliseconds()
        val effectiveMs = wallClockMs % totalDurationMs
        var lo = 0
        var hi = sections.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val sectionTiming = sections[mid]
            when {
                effectiveMs < sectionTiming.startTimeMs -> hi = mid - 1
                effectiveMs >= sectionTiming.startTimeMs + sectionTiming.durationMs -> lo = mid + 1
                else -> return mid
            }
        }
        return 0
    }

    private fun buildPlaySection(timing: SectionTiming, beginAt: Duration = Duration.ZERO): PlaySection {
        return PlaySection(
            track = PlayItem.Track(timing.track, beginAt),
            stinger = timing.stinger?.let { PlayItem.Stinger(it) },
            dj = timing.dj?.let { PlayItem.Dj(it) },
        )
    }

    private fun fallbackSection(): PlaySection {
        val track = station.track.first()
        return PlaySection(track = PlayItem.Track(track))
    }

    // SplitMix64
    private fun hash64(a: Long, b: Long): Long {
        var x = a xor (b * -7046029254386353131L) // 0x9E3779B97F4A7C15
        x = (x xor (x ushr 30)) * -4658895280553007687L // 0xBF58476D1CE4E5B9
        x = (x xor (x ushr 27)) * -7723592293110705685L // 0x94D049BB133111EB
        return x xor (x ushr 31)
    }

    private fun <T> shuffle(list: List<T>, rng: Random): List<T> {
        val result = list.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    private fun <T> pickRecent(pool: List<T>, recent: ArrayDeque<T>, rng: Random): T {
        val candidates = if (pool.size > recent.size) pool.filter { it !in recent } else pool
        val picked = if (candidates.isNotEmpty()) candidates[rng.nextInt(candidates.size)]
                     else pool[rng.nextInt(pool.size)]
        recent.addLast(picked)
        if (recent.size > 4) recent.removeFirst()
        return picked
    }
}
