package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.constants.SUPPORTED_FORMATS
import io.github.miuzarte.fhradio.model.*
import java.io.File

actual class AudioScanner {

    actual fun verifyOnly(config: RadioConfig, folderPath: String): VerifyResult {
        val results = config.stations
            .filterNot { it.isCrossStation }
            .map { station ->
            val stationDir = File(folderPath, station.name)
            if (!stationDir.isDirectory) {
                StationVerifyResult(
                    station.name, false,
                    0, station.tracks.size,
                    0, station.stingers.size,
                    0, station.djSamples.size,
                )
            } else {
                val trackDir = File(stationDir, SampleType.Track.toString())
                val stingerDir = File(stationDir, SampleType.Stinger.toString())
                val djDir = File(stationDir, SampleType.DJ.toString())

                val trackFiles = listDirFiles(trackDir)
                val stingerFiles = listDirFiles(stingerDir)
                val djFiles = listDirFiles(djDir)

                val trackMatched = station.tracks.count { t ->
                    SUPPORTED_FORMATS.any { ext -> "${t.soundName}.$ext" in trackFiles }
                }

                StationVerifyResult(
                    station.name, true,
                    trackMatched, station.tracks.size,
                    stingerFiles.size, station.stingers.size,
                    djFiles.size, station.djSamples.size,
                )
            }
        }
        return VerifyResult(results)
    }

    private fun listDirFiles(dir: File): Set<String> {
        if (!dir.isDirectory) return emptySet()
        val result = mutableSetOf<String>()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.extension.lowercase() in SUPPORTED_FORMATS) {
                result.add(file.name.lowercase())
            }
        }
        // 递归扫描子目录 (多 bank 如 CU1/Disk)
        dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
            val subResult = listDirFiles(subDir)
            result.addAll(subResult)
        }
        return result
    }
}
