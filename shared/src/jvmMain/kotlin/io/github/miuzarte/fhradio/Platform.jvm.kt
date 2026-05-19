package io.github.miuzarte.fhradio

import java.io.File

actual fun readFileTextOrNull(path: String): String? = runCatching {
    File(path).readText()
}.getOrNull()

actual fun fileExists(path: String): Boolean = File(path).exists()
