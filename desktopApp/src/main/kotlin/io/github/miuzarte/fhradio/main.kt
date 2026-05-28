package io.github.miuzarte.fhradio

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.miuzarte.fhradio.pages.MainScreen

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1080.dp, 960.dp))

    Window(
        onCloseRequest = {
            Scheduler.dispose()
            Radio.dispose()
            AppRuntime.dispose()
            exitApplication()
        },
        state = windowState,
        title =
            if (Radio.selectedStation == null || Radio.trackSlot.playing == null) "FHRadio"
            else "${Radio.trackSlot.playing!!.displayName} - ${Radio.trackSlot.playing!!.artist} - ${Radio.selectedStation!!.name} - FHRadio",
    ) {
        MainScreen()
    }
}
