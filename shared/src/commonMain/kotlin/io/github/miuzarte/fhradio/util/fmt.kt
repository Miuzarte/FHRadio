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

private fun Long.pad(length: Int = 0): String =
    toString().padStart(length, '0')

fun Duration.format(): String {
    val totalMillis = inWholeMilliseconds

    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = (totalMillis % 1_000) / 10

    return if (minutes > 0) {
        "$minutes:${seconds.pad(2)}.${millis.pad(3)}"
    } else {
        "$seconds.${millis.pad(3)}"
    }
}
