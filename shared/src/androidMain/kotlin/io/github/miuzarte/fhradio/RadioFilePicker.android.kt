package io.github.miuzarte.fhradio

actual class RadioFilePicker {
    actual val pickedXmlPath: String? = null

    actual fun pickAndRead(): String? {
        TODO("Android file picker not yet implemented")
    }
    actual fun pickFolder(): String? {
        TODO("Android folder picker not yet implemented")
    }
}
