package io.github.miuzarte.fhradio

import com.russhwolf.settings.Settings
import okio.Path.Companion.toPath

actual fun readFileTextOrNull(path: String): String? = null
actual fun fileExists(path: String): Boolean = false
actual fun joinPath(base: String, relative: String): String = (base.toPath() / relative).toString()

actual fun platformSettings(): Settings = Settings()

actual val needVolumeSync: Boolean = false
actual val audioDuckingDefault: Boolean = true

actual fun startForegroundService() {}
actual fun stopForegroundService() {}
