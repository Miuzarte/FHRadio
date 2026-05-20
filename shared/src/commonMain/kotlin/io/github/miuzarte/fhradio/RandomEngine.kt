package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.DjSample
import io.github.miuzarte.fhradio.model.RadioStation
import io.github.miuzarte.fhradio.model.Sample
import io.github.miuzarte.fhradio.model.SampleType
import io.github.miuzarte.fhradio.model.StingerSample
import io.github.miuzarte.fhradio.model.TrackSample
import kotlin.random.Random
import kotlin.time.Duration

class RandomEngine(station: RadioStation) : RadioModeEngine(station) {
    override fun scheduleModeMarkers(sample: Sample, beginAt: Duration) {
        when (sample) {
            is TrackSample -> {
                if (sample.stingerStart > 0 && Random.nextInt(100) < Radio.settings.stingerChance)
                    Scheduler.scheduleMarker(
                        tag = "Track.StingerStart",
                        sample = sample,
                        targetPos = sample.stingerStart,
                        beginAt = beginAt,
                    ) {
                        debugSnack("Track.StingerStart @ ${sample.stingerStart}")
                        Radio.trackOnStingerStart()
                    }
                if (sample.djStart > 0 && Random.nextInt(100) < Radio.settings.djChance)
                    Scheduler.scheduleMarker(
                        tag = "Track.DJStart",
                        sample = sample,
                        targetPos = sample.djStart,
                        beginAt = beginAt,
                    ) {
                        debugSnack("Track.DJStart @ ${sample.djStart}")
                        Radio.onDjStart()
                    }
            }

            is StingerSample -> {
                if (sample.startNextTrack > 0)
                    Scheduler.scheduleMarker(
                        tag = "Stinger.StartNextTrack",
                        sample = sample,
                        targetPos = sample.startNextTrack,
                        beginAt = beginAt,
                    ) {
                        debugSnack("Stinger.StartNextTrack @ ${sample.startNextTrack}")
                        Radio.stingerOnStartNextTrack()
                    }
            }

            is DjSample -> {}
        }
    }

    override fun getResume() {
        Radio.playRandom(SampleType.Track)
    }

    override fun advance() {
        Radio.playNext(SampleType.Track)
    }

    override fun getNext(type: SampleType, step: Int, exclude: Set<Sample>): Sample? {
        return Radio.randomSample(type, exclude)
    }

    override fun reset() {
        // nothing to reset
    }
}
