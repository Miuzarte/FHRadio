package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.DjSample
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.model.StingerSample
import io.github.miuzarte.fhradio.model.TrackSample
import kotlin.random.Random
import kotlin.time.Duration

class RandomEngine : RadioModeEngine() {
    override fun scheduleModeMarkers(sample: Sample, beginAt: Duration) {
        when (sample) {
            is TrackSample -> {
                if (sample.stingerStart > 0 && Random.nextInt(100) < Radio.settings.stingerChance)
                    Radio.scheduleMarker("Track.StingerStart", sample, sample.stingerStart, beginAt) {
                        debugDo { AppRuntime.snackbar("Track.StingerStart @ ${sample.stingerStart}") }
                        Radio.onStingerStart()
                    }
                if (sample.djStart > 0 && Random.nextInt(100) < Radio.settings.djChance)
                    Radio.scheduleMarker("Track.DJStart", sample, sample.djStart, beginAt) {
                        debugDo { AppRuntime.snackbar("Track.DJStart @ ${sample.djStart}") }
                        Radio.onDjStart()
                    }
            }

            is StingerSample -> {
                if (sample.startNextTrack > 0)
                    Radio.scheduleMarker("Stinger.StartNextTrack", sample, sample.startNextTrack, beginAt) {
                        debugDo { AppRuntime.snackbar("Stinger.StartNextTrack @ ${sample.startNextTrack}") }
                        Radio.onStingerNextTrack()
                    }
            }

            is DjSample -> {}
        }
    }

    override fun resume() {
        Radio.playRandom(SampleType.Track)
    }

    override fun advance() {
        Radio.playNext(SampleType.Track)
    }

    override fun nextSample(type: SampleType, step: Int, exclude: Set<Sample>): Sample? {
        return Radio.randomSample(type, exclude)
    }
}
