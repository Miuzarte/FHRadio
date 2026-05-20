package io.github.miuzarte.fhradio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.miuzarte.fhradio.model.Sample
import kotlinx.coroutines.*
import kotlin.time.*

class ScheduledJob(
    val job: Job,
    val tag: String,
    val scheduledAt: Instant,
    val delay: Duration,

    // for debug
    val targetPos: Int,
    val total: Duration,
) {
    val fireAt: Instant get() = scheduledAt + delay

    fun remaining(now: Instant = Clock.System.now()): Duration =
        (fireAt - now).coerceAtLeast(Duration.ZERO)

    val hasFired: Boolean get() = remaining() <= Duration.ZERO

    fun cancel() = job.cancel()
}

// for debug
data class ScheduledInfo(
    val tag: String,
    val targetPos: Int,
    val total: Duration,
    val remain: Duration,
    val fireAt: Instant,
)

object Scheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableListOf<ScheduledJob>()

    var debugMarkers: List<ScheduledInfo> by mutableStateOf(emptyList())
        private set

    fun scheduleMarker(
        tag: String,
        sample: Sample,
        targetPos: Int,
        beginAt: Duration,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        val total = (targetPos * 1000L / sample.sampleRate).toDuration(DurationUnit.MILLISECONDS)
        val delay = (total - beginAt).coerceAtLeast(Duration.ZERO)
        val now = Clock.System.now()
        val job = scope.launch {
            if (delay.isPositive()) delay(delay)
            block()
        }
        jobs.add(ScheduledJob(job, tag, now, delay, targetPos, total))

        // debug
        val totalSec = delay.inWholeSeconds
        val min = totalSec / 60
        val sec = totalSec % 60
        val timeStr = """${if (min > 0) "${min}m" else ""}${sec}s"""
        debugSnack("派发: $tag | ${timeStr}后 | @$targetPos")
        syncDebug()
    }

    fun cancel(tag: String) {
        jobs.removeAll { sj ->
            val ok =
                if (sj.tag == tag) {
                    sj.cancel()
                    true
                } else false
            debugSnack("(ok:$ok)取消: $tag")
            ok
        }

        syncDebug()
    }

    fun cancel() {
        jobs.forEach { it.cancel() }
        jobs.clear()

        syncDebug()
    }

    private fun syncDebug() {
        if (!BuildKonfig.DEBUG) return

        val now = Clock.System.now()
        debugMarkers = jobs.map { sj ->
            ScheduledInfo(
                tag = sj.tag,
                targetPos = sj.targetPos,
                total = sj.total,
                remain = sj.remaining(now),
                fireAt = sj.fireAt,
            )
        }
    }
}
