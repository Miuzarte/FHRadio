package io.github.miuzarte.fhradio

import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

object Scheduler {

    class Job(
        val tag: String,
        val triggerPosition: Duration,
        val player: AudioPlayer,
        val block: () -> Unit,
    ) {
        val createdAt: Instant = Clock.System.now()
        val scheduledAt: Instant get() = createdAt + triggerPosition
        val remaining: Duration get() = (triggerPosition - player.state.position).coerceAtLeast(Duration.ZERO)
        val hasFired: Boolean get() = remaining <= Duration.ZERO
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    internal val jobs = mutableStateListOf<Job>()

    private var watcherJob: kotlinx.coroutines.Job? = null

    fun scheduleMarker(
        tag: String,
        triggerPosition: Duration,
        player: AudioPlayer,
        block: () -> Unit,
    ) {
        cancel(tag)
        jobs.add(Job(tag, triggerPosition, player, block))
        ensureWatching()
    }

    fun cancel(tag: String, onlyWhenFired: Boolean = false) {
        if (onlyWhenFired) jobs.removeAll { it.tag == tag && it.hasFired }
        else jobs.removeAll { it.tag == tag }
    }

    fun cancel(vararg tags: String, onlyWhenFired: Boolean = false) {
        val tagSet = tags.toSet()
        if (onlyWhenFired) jobs.removeAll { it.tag in tagSet && it.hasFired }
        else jobs.removeAll { it.tag in tagSet }
    }

    fun cancel() {
        jobs.clear()
        watcherJob?.cancel()
        watcherJob = null
    }

    fun dispose() = cancel()

    // 实现 marker 触发
    private fun ensureWatching() {
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch {
            while (isActive) {
                val snapshot = jobs.toList()
                val toFire = snapshot
                    .filter { it.player.getComputedPosition() >= it.triggerPosition }
                    .sortedBy { it.triggerPosition }

                for (job in toFire) {
                    if (job in jobs) {
                        jobs.remove(job)
                        jobs.removeAll { it.tag == job.tag }
                        job.block()
                    }
                }

                delay(20.milliseconds)
            }
        }
    }
}
