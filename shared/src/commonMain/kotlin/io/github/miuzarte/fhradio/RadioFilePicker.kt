package io.github.miuzarte.fhradio

expect class RadioFilePicker() {
    var pickedXmlPath: String?
    suspend fun pickAndRead(): String?
    suspend fun pickFolder(): String?
}
