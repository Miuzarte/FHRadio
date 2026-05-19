package io.github.miuzarte.fhradio.util

import kotlin.math.roundToInt


fun Float.fmt(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}

fun Double.fmt(): String {
    val r = (this * 10).roundToInt()
    return "${r / 10}.${r % 10}"
}
