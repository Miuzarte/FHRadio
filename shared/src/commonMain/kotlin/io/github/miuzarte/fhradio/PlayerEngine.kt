package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.PlayMode
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.model.StingerSample
import io.github.miuzarte.fhradio.model.TrackSample
import kotlin.random.Random
import kotlin.time.Duration

class PlayerEngine : RadioModeEngine() {
    private var patternIndex = 0

    override fun resetPatternState() {
        patternIndex = 0
    }

    override fun onSamplePlayed(sample: Sample) {
        resetPatternIndex(sample.type)
    }

    override fun scheduleModeMarkers(sample: Sample, beginAt: Duration) {
        when (sample) {
            is TrackSample -> {}
            is StingerSample -> {
                if (sample.startNextTrack > 0 && Radio.settings.crossFadeEnabled)
                    Radio.scheduleMarker("Stinger.StartNextTrack", sample, sample.startNextTrack, beginAt) {
                        debugDo { AppRuntime.snackbar("Stinger.StartNextTrack @ ${sample.startNextTrack}") }
                        Radio.onStingerNextTrack()
                    }
            }

            is io.github.miuzarte.fhradio.model.DjSample -> {}
        }
    }

    override fun resume() {
        val pbState = SettingsStore.loadPlaybackState()
        val name = pbState.soundName
        val pos = pbState.position
        val sampleType = when (pbState.type) {
            "Track" -> SampleType.Track
            "Stinger" -> SampleType.Stinger
            "DJ" -> SampleType.DJ
            else -> return
        }

        if (name != null) {
            val resumedOk = when (sampleType) {
                SampleType.Track -> Radio.selectedTracks?.find { it.soundName == name }
                    ?.let { Radio.beginSample(it, pos) }
                SampleType.Stinger -> Radio.selectedStingers?.find { it.soundName == name }
                    ?.let { Radio.beginSample(it, pos) }
                SampleType.DJ -> Radio.selectedDj?.find { it.soundName == name }
                    ?.let { Radio.beginSample(it, pos) }
            }
            if (resumedOk == true) return
        }

        when (Radio.settings.playMode) {
            PlayMode.Shuffle -> Radio.playNext(sampleType)
            PlayMode.Order -> {
                val list = Radio.selectedTracks ?: return
                if (list.isNotEmpty()) Radio.beginSample(list.first())
            }
        }
    }

    override fun advance() {
        if (Radio.settings.patternEnabled) {
            Radio.patternNodes.takeIf { it.isNotEmpty() }
                ?.let { advanceByPattern(it) }
                ?: Radio.playNext(SampleType.Track)
        } else {
            Radio.crossLists.takeIf { it.isNotEmpty() }
                ?.let { advanceByCrossList(it) }
                ?: Radio.playNext(SampleType.Track)
        }
    }

    private fun advanceByPattern(nodes: List<PatternNode>) {
        Radio.selectedStation ?: return
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
        Radio.playNext(SampleType.Track)
    }

    private fun advanceByCrossList(crossLists: List<SampleType>) {
        Radio.selectedStation ?: return
        val lastSample = Radio.lastPlayedSample
        val orderMode = Radio.settings.playMode == PlayMode.Order

        if (orderMode && lastSample != null) {
            val sample = nextSample(lastSample.type)
            if (sample != null) {
                Radio.beginSample(sample)
                return
            }
        }

        val currentIdx = crossLists.indexOf(lastSample?.type ?: crossLists.first())
        val nextType = if (currentIdx >= 0) crossLists[(currentIdx + 1) % crossLists.size] else crossLists.first()
        val list = Radio.sampleList(nextType)?.filter { it.resolvePath() != null }
        if (list.isNullOrEmpty()) {
            Radio.playNext(nextType)
            return
        }
        val sample = nextSample(nextType)
        if (sample != null) Radio.beginSample(sample)
        else Radio.playNext(nextType)
    }

    override fun nextSample(type: SampleType, step: Int, exclude: Set<Sample>): Sample? {
        return when (Radio.settings.playMode) {
            PlayMode.Shuffle -> {
                Radio.randomSample(type, exclude)
            }

            PlayMode.Order -> {
                val list = Radio.sampleList(type) ?: return null
                val idx = Radio.lastPlayedSample
                    ?.takeIf { it.type == type }
                    ?.let { list.indexOf(it) }
                    ?: -1
                if (idx >= 0) list[(idx + step) % list.size]
                else list.first()
            }
        }
    }

    private fun playNode(node: PatternNode): Boolean {
        Radio.selectedStation ?: return false
        val type = node.type
        val list = Radio.sampleList(type)?.filter { it.resolvePath() != null }
        if (list.isNullOrEmpty()) return false

        val lastOfType = when (type) {
            SampleType.Track -> Radio.lastTrack
            SampleType.Stinger -> Radio.lastStinger
            SampleType.DJ -> Radio.lastDj
        }
        val orderMode = Radio.settings.playMode == PlayMode.Order
        val sample = if (orderMode) {
            val idx = lastOfType?.let { list.indexOf(it) }?.takeIf { it >= 0 } ?: -1
            if (idx >= 0) list[(idx + node.step) % list.size] else list.first()
        } else {
            list[Random.nextInt(list.size)]
        }
        Radio.stopMain()
        Radio.stopSecondary()
        return Radio.beginSample(sample, solo = false)
    }

    private fun resetPatternIndex(type: SampleType) {
        val nodes = Radio.patternNodes.takeIf { it.isNotEmpty() } ?: run {
            patternIndex = 0
            return
        }
        val found = nodes.indexOfFirst { it.type == type }
        patternIndex = if (found >= 0) (found + 1) % nodes.size else 0
    }
}
