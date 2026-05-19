package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.RadioConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.measureTimedValue

suspend fun importRadio(): ImportResult {
    return withContext(Dispatchers.Default) {
        val picker = RadioFilePicker()
        val xml = picker.pickAndRead()
            ?: return@withContext ImportResult.Cancelled
        val xmlPath = picker.pickedXmlPath!!

        val (result, parseCost) = measureTimedValue { RadioXmlParser.parse(xml) }
        AppRuntime.snackbar("parse: ${parseCost.inWholeMilliseconds}ms")

        val folder = picker.pickFolder()
            ?: return@withContext ImportResult.Cancelled

        val verify = AudioScanner().verifyOnly(result, folder)

        val totalTracks = result.stations.sumOf { it.tracks.size }
        val totalStingers = result.stations.sumOf { it.stingers.size }
        val totalDj = result.stations.sumOf { it.djSamples.size }

        if (!verify.anyMatched) {
            withContext(Dispatchers.Main) {
                AppRuntime.snackbar("导入失败: 没有匹配到任何音频文件")
                verify.warnings.forEach { AppRuntime.snackbar(it) }
            }
            return@withContext ImportResult.Cancelled
        }

        withContext(Dispatchers.Main) {
            AppRuntime.snackbar("导入成功: ${result.stations.size}电台 | $totalTracks 曲目 | $totalStingers Stinger | $totalDj DJ语音")
            verify.warnings.forEach { AppRuntime.snackbar(it) }
        }

        ImportResult.Success(xmlPath, folder, result)
    }
}

sealed class ImportResult {
    data class Success(
        val xmlPath: String,
        val audioPath: String,
        val config: RadioConfig,
    ) : ImportResult()
    data object Cancelled : ImportResult()
}
