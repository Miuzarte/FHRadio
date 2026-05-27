package io.github.miuzarte.fhradio

import java.awt.FileDialog
import java.awt.Frame
import javax.swing.JFileChooser
import java.io.File

actual class RadioFilePicker {
    actual var pickedXmlPath: String? = null

    actual suspend fun pickAndRead(): String? {
        val dialog = FileDialog(null as Frame?, "选择电台 XML 文件", FileDialog.LOAD)
        dialog.file = "*.xml"
        dialog.isVisible = true
        val file = dialog.file ?: return null
        pickedXmlPath = File(dialog.directory, file).absolutePath
        return File(dialog.directory, file).readText()
    }

    actual suspend fun pickFolder(): String? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "选择音频文件夹"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile.absolutePath
    }
}
