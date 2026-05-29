package io.github.miuzarte.fhradio

import android.net.Uri
import android.provider.DocumentsContract
import com.russhwolf.settings.Settings
import okio.Path.Companion.toPath
import java.io.File

actual fun readFileTextOrNull(path: String): String? {
    if (path.startsWith("content://")) {
        return runCatching {
            val input = AndroidBridge.activity.contentResolver.openInputStream(Uri.parse(path))
            input?.use { it.readBytes().decodeToString() }
        }.getOrNull()
    }
    return runCatching { File(path).readText() }.getOrNull()
}

actual fun fileExists(path: String): Boolean {
    if (path.startsWith("content://")) {
        return runCatching {
            AndroidBridge.activity.contentResolver.openInputStream(Uri.parse(path))?.close()
            true
        }.getOrDefault(false)
    }
    return File(path).exists()
}

actual fun joinPath(base: String, relative: String): String {
    if (!base.startsWith("content://")) {
        return (base.toPath() / relative).toString()
    }
    val treeUri = Uri.parse(base)
    val docId = "${DocumentsContract.getTreeDocumentId(treeUri)}/$relative"
    return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
}

actual fun platformSettings(): Settings = Settings()

actual val needVolumeSync: Boolean = false
actual val audioDuckingDefault: Boolean = true

actual fun startForegroundService() {
    val context = AndroidBridge.appContext
    RadioForegroundService.start(context)
}

actual fun stopForegroundService() {
    val context = AndroidBridge.appContext
    RadioForegroundService.stop(context)
}
