package io.github.miuzarte.fhradio

actual class RadioFilePicker {
    actual var pickedXmlPath: String? = null

    actual suspend fun pickAndRead(): String? {
        TODO("iOS file picker not yet implemented")
    }
    actual suspend fun pickFolder(): String? {
        TODO("iOS folder picker not yet implemented")
    }
}
