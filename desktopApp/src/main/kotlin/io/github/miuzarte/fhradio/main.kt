package io.github.miuzarte.fhradio

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import fhradio.shared.generated.resources.Res
import fhradio.shared.generated.resources.ic_launcher
import io.github.miuzarte.fhradio.pages.MainScreen
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

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
        icon = painterResource(Res.drawable.ic_launcher),
        title =
            if (Radio.selectedStation == null || Radio.trackSlot.playing == null) "FHRadio"
            else "${Radio.trackSlot.playing!!.displayName} - ${Radio.trackSlot.playing!!.artist} - ${Radio.selectedStation!!.name} - FHRadio",
    ) {
        window.minimumSize = Dimension(384, 720)
        MainScreen()
    }
}
