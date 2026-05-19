package io.github.miuzarte.fhradio.util

import kotlin.math.roundToInt
import kotlin.time.Duration


fun Float.fmt(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}

fun Double.fmt(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}

fun Double.fmtDurationSec(): String {
    val inMs = (this * 1000).toInt()
    val inSec = this.toInt()
    val min = inSec / 60
    val sec = inSec % 60
    return """${if (min > 0) "${min}m" else ""}${sec}s"""
}

private fun Long.pad2(): String =
    toString().padStart(2, '0')

fun Duration.format(): String {
    val totalMillis = inWholeMilliseconds

    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = (totalMillis % 1_000) / 10

    return if (minutes > 0) {
        "$minutes:${seconds.pad2()}.${millis.pad2()}"
    } else {
        "$seconds.${millis.pad2()}"
    }
}
