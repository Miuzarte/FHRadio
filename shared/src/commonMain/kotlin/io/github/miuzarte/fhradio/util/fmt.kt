package io.github.miuzarte.fhradio.util

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Instant


fun Float.fmt(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}

fun Double.fmt(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}

private fun Int.pad(length: Int = 0): String =
    toString().padStart(length, '0')

private fun Long.pad(length: Int = 0): String =
    toString().padStart(length, '0')

fun Duration.format(): String {
    val totalMillis = inWholeMilliseconds

    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = (totalMillis % 1_000) / 10

    return "$minutes:${seconds.pad(2)}.${millis.pad(3)}"
}

fun Instant.formatTime(): String {
    val secondsOfDay = epochSeconds % (24 * 3600)
    val h = secondsOfDay / 3600
    val m = (secondsOfDay % 3600) / 60
    val s = secondsOfDay % 60
    val ms = (nanosecondsOfSecond + 500_000) / 1_000_000

    return "${h.pad(2)}:${m.pad(2)}:${s.pad(2)}.${ms.pad(3)}"
}
