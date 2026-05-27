package io.github.miuzarte.fhradio

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.miuzarte.fhradio.constants.SUPPORTED_FORMATS
import io.github.miuzarte.fhradio.model.RadioConfig
import io.github.miuzarte.fhradio.model.SampleType

actual class AudioScanner {

    actual fun verifyOnly(config: RadioConfig, folderPath: String): VerifyResult {
        val context = AndroidBridge.activity
        val treeUri = Uri.parse(folderPath)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return VerifyResult(emptyList())

        val results = config.stations
            .filterNot { it.isCrossStation }
            .map { station ->
                val stationDir = root.findFile(station.name)
                if (stationDir == null || !stationDir.isDirectory) {
                    StationVerifyResult(
                        station.name, false,
                        0, station.tracks.size,
                        0, station.stingers.size,
                        0, station.djSamples.size,
                    )
                } else {
                    val trackDir = stationDir.findFile(SampleType.Track.toString())
                    val stingerDir = stationDir.findFile(SampleType.Stinger.toString())
                    val djDir = stationDir.findFile(SampleType.DJ.toString())

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

    private fun listDirFiles(dir: DocumentFile?): Set<String> {
        if (dir == null || !dir.isDirectory) return emptySet()
        val files = dir.listFiles()
        val result = mutableSetOf<String>()
        for (f in files) {
            if (f.isFile) {
                f.name?.lowercase()?.let { name ->
                    if (SUPPORTED_FORMATS.any { name.endsWith(".$it") }) result.add(name)
                }
            } else if (f.isDirectory) {
                result.addAll(listDirFiles(f))
            }
        }
        return result
    }
}
