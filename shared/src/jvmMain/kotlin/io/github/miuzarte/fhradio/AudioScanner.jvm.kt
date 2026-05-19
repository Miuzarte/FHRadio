package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.*
import java.io.File

actual class AudioScanner {

    actual fun verifyOnly(config: RadioConfig, folderPath: String): VerifyResult {
        val results = config.stations.map { station ->
            val stationDir = File(folderPath, station.name)
            if (!stationDir.isDirectory) {
                StationVerifyResult(station.name, false, 0, station.tracks.size, 0, station.djSamples.size, 0, station.stingers.size)
            } else {
                val trackDir = File(stationDir, "Track")
                val djDir = File(stationDir, "DJ")
                val stingerDir = File(stationDir, "Stinger")

                val trackMatched = station.tracks.count { t -> hasFile(trackDir, t.soundName) != null }
                val djCount = countAudioFiles(djDir)
                val stingerCount = countAudioFiles(stingerDir)

                StationVerifyResult(
                    station.name, true,
                    trackMatched, station.tracks.size,
                    djCount, station.djSamples.size,
                    stingerCount, station.stingers.size,
                )
            }
        }
        return VerifyResult(results)
    }

    private fun hasFile(dir: File, soundName: String): String? {
        if (!dir.isDirectory) return null
        for (ext in audioExtensions) {
            if (File(dir, "$soundName.$ext").exists()) return ext
        }
        return null
    }

    private fun countAudioFiles(dir: File): Int {
        if (!dir.isDirectory) return 0
        return dir.listFiles()?.count { it.isFile && it.extension.lowercase() in audioExtensions } ?: 0
    }

    companion object {
        val audioExtensions = listOf("wav", "flac", "mp3", "opus")
    }
}
