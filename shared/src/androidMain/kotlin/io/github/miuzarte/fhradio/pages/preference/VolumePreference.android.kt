package io.github.miuzarte.fhradio.pages.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.scaffolds.ArrowSlider

@Composable
actual fun VolumePreference() {
    var volume by remember(AppSettings.volume) {
        mutableStateOf(AppSettings.volume.toFloat())
    }

    ArrowSlider(
        title = "音量",
        value = volume,
        onValueChange = {
            volume = it
            AppRuntime.setVolume(it.toInt())
        },
        onValueChangeFinished = {
            AppSettings.volume = volume.toInt()
        },
        valueRange = 0f..100f,
        steps = 100,
        unit = "%",
        displayFormatter = { "${it.toInt()}" },
        inputInitialValue = "${AppSettings.volume}",
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..100f,
        onInputConfirm = { input ->
            input.toIntOrNull()?.let {
                val v = it.coerceIn(0, 100)
                volume = v.toFloat()
                AppRuntime.setVolume(v)
                AppSettings.volume = v
            }
        },
    )
}
