package io.github.miuzarte.fhradio

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import okio.Path.Companion.toPath
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

actual fun readFileTextOrNull(path: String): String? = runCatching {
    File(path).readText()
}.getOrNull()

actual fun fileExists(path: String): Boolean = File(path).exists()
actual fun joinPath(base: String, relative: String): String = (base.toPath() / relative).toString()

actual fun platformSettings(): Settings {
    val file = File(System.getProperty("user.home")!!, ".config/fhradio/settings.properties")
    file.parentFile!!.mkdirs()
    val props = Properties().also {
        if (file.exists()) FileInputStream(file).use { fis -> it.load(fis) }
    }
    return PropertiesSettings(
        delegate = props,
        onModify = { p ->
            FileOutputStream(file).use { fos -> p.store(fos, null) }
        },
    )
}

actual val needVolumeSync: Boolean = true
actual val audioDuckingDefault: Boolean = false

actual fun startForegroundService() {}
actual fun stopForegroundService() {}
