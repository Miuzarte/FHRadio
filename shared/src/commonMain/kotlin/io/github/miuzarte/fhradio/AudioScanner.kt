package io.github.miuzarte.fhradio

import io.github.miuzarte.fhradio.model.RadioInfo

data class VerifyResult(
    val stationResults: List<StationVerifyResult>,
) {
    val anyMatched: Boolean get() = stationResults.any { it.matched }
    val warnings: List<String> get() = stationResults.flatMap { it.warnings }
}

data class StationVerifyResult(
    val stationName: String,
    val folderExists: Boolean,
    val trackMatched: Int,
    val trackTotal: Int,
    val stingerCount: Int,
    val stingerXmlCount: Int,
    val djCount: Int,
    val djXmlCount: Int,
) {
    val matched: Boolean get() = folderExists && (trackMatched > 0 || djCount > 0 || stingerCount > 0)
    val warnings: List<String>
        get() = buildList {
            if (!folderExists) add("$stationName: 文件夹不存在, 已跳过")
            else if (!matched) add("$stationName: 无任何音频文件匹配")
            else {
                if (trackMatched < trackTotal) add("$stationName: Track ${trackMatched}/${trackTotal} 匹配")
                if (djCount != djXmlCount) add("$stationName: DJ 数量不匹配 (目录${djCount}, XML${djXmlCount})")
                if (stingerCount != stingerXmlCount) add("$stationName: Stinger 数量不匹配 (目录${stingerCount}, XML${stingerXmlCount})")
            }
        }
}

expect class AudioScanner() {
    fun verifyOnly(config: RadioInfo, folderPath: String): VerifyResult
}
