package io.github.miuzarte.fhradio

import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ScheduledJob(
    val job: Job,
    val tag: String,
    val createdAt: Instant = Clock.System.now(),
    val delay: Duration,
) {

    // 触发时刻
    val scheduledAt: Instant get() = createdAt + delay

    // 剩余时间
    fun remaining(now: Instant = Clock.System.now()): Duration =
        (scheduledAt - now).coerceAtLeast(Duration.ZERO)

    val hasFired: Boolean get() = remaining() <= Duration.ZERO

    fun cancel() = job.cancel()
}

object Scheduler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = mutableListOf<ScheduledJob>()

    fun scheduleMarker(
        tag: String,
        delay: Duration,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        cancel(tag)

        scope.launch {
            val tag = tag
            val delay = delay
            val block = block

            if (delay.isPositive()) delay(delay)
            block()

            scope.launch {
                // 五秒后自动删除
                delay(5.seconds)
                jobs.removeAll { it.tag == tag }
            }
        }.also {
            jobs.add(
                ScheduledJob(
                    job = it,
                    tag = tag,
                    delay = delay,
                )
            )
        }
    }

    fun scheduleMarker(
        tag: String,
        scheduledAt: Instant,
        block: suspend CoroutineScope.() -> Unit,
    ) = scheduleMarker(
        tag = tag,
        delay = scheduledAt - Clock.System.now(),
        block = block,
    )

    fun cancel(tag: String) {
        jobs.removeAll {
            val ok =
                if (it.tag == tag) {
                    it.cancel()
                    true
                } else false
            // debugSnack("(ok:$ok)取消: $tag")
            ok
        }
    }

    fun cancel(vararg tag: String) {
        val tag = tag.toSet()

        jobs.removeAll {
            val ok =
                if (it.tag in tag) {
                    it.cancel()
                    true
                } else false
            // debugSnack("(ok:$ok)取消: $tag")
            ok
        }
    }

    fun cancel() {
        jobs.forEach { it.cancel() }
        jobs.clear()
    }

    fun dispose() = cancel()
}
