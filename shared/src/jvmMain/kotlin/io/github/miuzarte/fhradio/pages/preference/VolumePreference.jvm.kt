package io.github.miuzarte.fhradio.pages.preference

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.AppRuntime
import io.github.miuzarte.fhradio.AppSettings
import io.github.miuzarte.fhradio.scaffolds.ArrowSlider
import top.yukonga.miuix.kmp.basic.SliderDefaults.SliderHapticEffect

@Composable
actual fun VolumePreference() {
    var volume by remember(AppSettings.volume) {
        mutableStateOf(AppSettings.volume.toFloat())
    }

    // 提供给 vlc(j) 的音量值经过开三次方处理,
    // 0..800 -> 0..200
    ArrowSlider(
        title = "音量",
        summary = "手动输入时允许的范围为 0..800",
        value = volume,
        onValueChange = {
            volume = it
            AppRuntime.setVolume(it.toInt())
        },
        onValueChangeFinished = {
            AppSettings.volume = volume.toInt()
        },
        valueRange = 0f..150f,
        steps = 0,
        hapticEffect = SliderHapticEffect.Step,
        showKeyPoints = true,
        keyPoints = listOf(0f, 100f, 150f),
        unit = "%",
        displayFormatter = { "${it.toInt()}" },
        inputInitialValue = "${AppSettings.volume}",
        inputFilter = { it.filter(Char::isDigit) },
        inputValueRange = 0f..800f,
        onInputConfirm = { input ->
            input.toIntOrNull()?.let {
                val v = it.coerceIn(0, 800)
                volume = v.toFloat()
                AppRuntime.setVolume(v)
                AppSettings.volume = v
            }
        },
    )
}
