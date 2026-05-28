package io.github.miuzarte.fhradio.util

import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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

fun Duration.format(withMs: Boolean = true): String {
    val totalMillis = inWholeMilliseconds
    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = (totalMillis % 1_000) / 10

    return buildString {
        append(minutes)
        append(':')
        append(seconds.pad(2))
        if (withMs) {
            append('.')
            append(millis.pad(2))
        }
    }
}

fun Instant.formatTime(offset: Duration = 8.hours, withMs: Boolean = true): String {
    // TODO: platforms timezone
    val totalSeconds = epochSeconds + offset.inWholeSeconds
    val secondsOfDay = (totalSeconds % (24 * 3600) + 24 * 3600) % (24 * 3600)
    val hours = secondsOfDay / 3600
    val minutes = (secondsOfDay % 3600) / 60
    val seconds = secondsOfDay % 60
    val millis = (nanosecondsOfSecond + 500_000) / 1_000_000  // 四舍五入取毫秒

    return buildString {
        append(hours.pad(2))
        append(':')
        append(minutes.pad(2))
        append(':')
        append(seconds.pad(2))
        if (withMs) {
            append('.')
            append(millis.pad(3))
        }
    }
}
