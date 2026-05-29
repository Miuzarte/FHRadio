package io.github.miuzarte.fhradio

import com.russhwolf.settings.Settings

expect fun readFileTextOrNull(path: String): String?
expect fun fileExists(path: String): Boolean
expect fun joinPath(base: String, relative: String): String

expect fun platformSettings(): Settings

expect val needVolumeSync: Boolean
expect val audioDuckingDefault: Boolean

expect fun startForegroundService()
expect fun stopForegroundService()
