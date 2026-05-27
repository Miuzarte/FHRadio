package io.github.miuzarte.fhradio

import okio.Path.Companion.toPath
import java.io.File

actual fun readFileTextOrNull(path: String): String? = runCatching {
    File(path).readText()
}.getOrNull()

actual fun fileExists(path: String): Boolean = File(path).exists()

actual val needVolumeSync: Boolean = true

actual fun joinPath(base: String, relative: String): String = (base.toPath() / relative).toString()
