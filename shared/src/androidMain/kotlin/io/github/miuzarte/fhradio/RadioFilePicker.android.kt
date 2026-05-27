package io.github.miuzarte.fhradio

actual class RadioFilePicker {
    actual var pickedXmlPath: String? = null

    actual suspend fun pickAndRead(): String? {
        val content = AndroidBridge.pickXml()
        if (content != null) pickedXmlPath = AndroidBridge.getLastXmlPrivatePath()
        return content
    }

    actual suspend fun pickFolder(): String? = AndroidBridge.pickFolderUri()
}
