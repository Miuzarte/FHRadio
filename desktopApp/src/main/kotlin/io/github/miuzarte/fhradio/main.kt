package io.github.miuzarte.fhradio

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.miuzarte.fhradio.pages.MainScreen

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(800.dp, 960.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title =
            if (Radio.selectedStation == null || Radio.trackPlaying == null) "FHRadio"
            else "${Radio.trackPlaying!!.displayName} - ${Radio.trackPlaying!!.artist} - ${Radio.selectedStation!!.name} - FHRadio",
    ) {
        MainScreen()
    }
}