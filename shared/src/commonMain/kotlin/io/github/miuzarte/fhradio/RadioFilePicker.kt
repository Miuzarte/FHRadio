package io.github.miuzarte.fhradio

expect class RadioFilePicker() {
    var pickedXmlPath: String?
    fun pickAndRead(): String?
    fun pickFolder(): String?
}
