package io.github.miuzarte.fhradio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AndroidBridge {
    lateinit var activity: ComponentActivity
        private set
    lateinit var appContext: Context
        private set
    lateinit var xmlLauncher: ActivityResultLauncher<Array<String>>
        private set
    lateinit var folderLauncher: ActivityResultLauncher<Uri?>
        private set

    private var xmlDeferred: CompletableDeferred<String?>? = null
    private var folderDeferred: CompletableDeferred<String?>? = null
    private var lastXmlPrivatePath: String? = null

    fun init(activity: ComponentActivity) {
        this.activity = activity
        this.appContext = activity.applicationContext
        xmlLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? -> onXmlResult(uri) }

        folderLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? -> onFolderResult(uri) }
    }

    private fun onXmlResult(uri: Uri?) {
        val deferred = xmlDeferred ?: return
        xmlDeferred = null
        if (uri == null) {
            deferred.complete(null)
            return
        }
        activity.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val inputStream = activity.contentResolver.openInputStream(uri)
        val content = inputStream?.use {
            it.readBytes().decodeToString()
        }
        lastXmlPrivatePath = copyXmlToPrivate(uri)
        deferred.complete(content)
    }

    private fun onFolderResult(uri: Uri?) {
        val deferred = folderDeferred ?: return
        folderDeferred = null
        if (uri != null) {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        deferred.complete(uri?.toString())
    }

    suspend fun pickXml(): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        xmlDeferred = deferred
        xmlLauncher.launch(arrayOf("text/xml", "*/*"))
        deferred.await()
    }

    suspend fun pickFolderUri(): String? = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<String?>()
        folderDeferred = deferred
        folderLauncher.launch(null)
        deferred.await()
    }

    fun getLastXmlPrivatePath(): String? = lastXmlPrivatePath

    private fun copyXmlToPrivate(uri: Uri): String {
        val name = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.xml"
        val dir = File(activity.filesDir, "radio")
        dir.mkdirs()
        val dest = File(dir, name)
        val inputStream = activity.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            dest.outputStream().use { output -> inputStream.copyTo(output) }
        }
        return dest.absolutePath
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = activity.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        ) ?: return null
        return cursor.use {
            if (it.moveToFirst()) {
                val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) it.getString(i)
                else null
            } else null
        }
    }
}
