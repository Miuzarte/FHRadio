package io.github.miuzarte.fhradio

actual class RadioFilePicker {
    actual val pickedXmlPath: String? = null

    actual fun pickAndRead(): String? {
        TODO("iOS file picker not yet implemented")
    }
    actual fun pickFolder(): String? {
        TODO("iOS folder picker not yet implemented")
    }
}
