package io.github.miuzarte.fhradio

import okio.Path.Companion.toPath

actual fun readFileTextOrNull(path: String): String? = null
actual fun fileExists(path: String): Boolean = false

actual val needVolumeSync: Boolean = false

actual fun joinPath(base: String, relative: String): String =
    (base.toPath() / relative).toString()
