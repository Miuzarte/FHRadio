package io.github.miuzarte.fhradio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

object AppRuntime {
    lateinit var mainPlayer: AudioPlayer
    lateinit var secondaryPlayer: AudioPlayer

    // --- Snackbar ---

    private val snackbarHostStateLock = Any()
    private val snackbarHostStateStack = mutableListOf<SnackbarHostState>()

    var snackbarHostState: SnackbarHostState?
        get() = synchronized(snackbarHostStateLock) { snackbarHostStateStack.lastOrNull() }
        set(value) {
            synchronized(snackbarHostStateLock) {
                snackbarHostStateStack.clear()
                if (value != null) snackbarHostStateStack.add(value)
            }
        }

    fun registerSnackbarHostState(hostState: SnackbarHostState): () -> Unit {
        synchronized(snackbarHostStateLock) { snackbarHostStateStack.add(hostState) }
        return { synchronized(snackbarHostStateLock) { snackbarHostStateStack.remove(hostState) } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun snackbar(
        message: String,
        duration: SnackbarDuration = SnackbarDuration.Short,
        onResult: ((SnackbarResult) -> Unit)? = null,
    ) {
        snackbarHostState?.let { host ->
            scope.launch {
                host.showSnackbar(message = message, duration = duration)
                    .let { onResult?.invoke(it) }
            }
        }
    }

    // --- Lifecycle ---

    fun dispose() {
        if (::mainPlayer.isInitialized) mainPlayer.dispose()
        if (::secondaryPlayer.isInitialized) secondaryPlayer.dispose()
    }
}
