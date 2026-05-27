package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

object AppRuntime {
    var debug by mutableStateOf(BuildKonfig.DEBUG)

    val mainPlayer = AudioPlayer()
    val secondaryPlayer = AudioPlayer()

    // --- Snackbar ---

    private val snackbarHostStateStack = mutableListOf<SnackbarHostState>()

    var snackbarHostState: SnackbarHostState?
        get() = snackbarHostStateStack.lastOrNull()
        set(value) {
            snackbarHostStateStack.clear()
            if (value != null) snackbarHostStateStack.add(value)
        }

    fun registerSnackbarHostState(hostState: SnackbarHostState): () -> Unit {
        snackbarHostStateStack.add(hostState)
        return { snackbarHostStateStack.remove(hostState) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun snackbar(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onResult: ((SnackbarResult) -> Unit)? = null,
    ) {
        snackbarHostState?.let { host ->
            scope.launch {
                host.showSnackbar(
                    message = message,
                    duration = duration,
                    withDismissAction = true,
                ).let { onResult?.invoke(it) }
            }
        }
    }

    // --- Lifecycle ---

    fun dispose() {
        mainPlayer.dispose()
        secondaryPlayer.dispose()
    }

    // --- Volume sync ---

    private var lastAppVolumeChangeEpochMs = 0L

    fun setVolume(volume: Int) {
        lastAppVolumeChangeEpochMs = Clock.System.now().toEpochMilliseconds()
        mainPlayer.setVolume(volume)
        secondaryPlayer.setVolume(volume)
    }

    private val volumeSyncJob = scope.launch(Dispatchers.Default) {
        while (isActive) {
            delay(1.seconds)
            syncVolumeFromPlayers()
        }
    }

    internal fun syncVolumeFromPlayers(force: Boolean = false) {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!force && now - lastAppVolumeChangeEpochMs < 100) return

        // 只取一个就行
        val vol = mainPlayer.getVolume()
        if (vol < 0) return

        if (kotlin.math.abs(vol - AppSettings.volume) > 1) {
            AppSettings.volume = vol
        }
    }
}
