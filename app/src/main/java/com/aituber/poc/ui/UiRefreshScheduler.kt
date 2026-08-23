package com.aituber.poc.ui

class UiRefreshScheduler<T>(
    private val postToUiDelayed: (() -> Unit, Long) -> Unit,
    private val nowMs: () -> Long,
    private val refreshIntervalMs: Long,
    private val render: (T) -> Unit
) {
    private var alive = true
    private var generation = 0
    private var latest: T? = null
    private var scheduled = false
    private var lastRenderMs: Long? = null

    @Synchronized
    fun submit(value: T) {
        latest = value
        if (scheduled) return
        scheduleLocked(delayMs = renderDelayLocked())
    }

    @Synchronized
    fun destroy() {
        alive = false
        generation += 1
        latest = null
        scheduled = false
    }

    @Synchronized
    private fun scheduleLocked(delayMs: Long) {
        if (!alive) return
        scheduled = true
        val scheduleGeneration = generation
        postToUiDelayed({
            val value = takeLatestForRender(scheduleGeneration) ?: return@postToUiDelayed
            render(value)
            finishRender(scheduleGeneration)
        }, delayMs)
    }

    @Synchronized
    private fun takeLatestForRender(scheduleGeneration: Int): T? {
        if (!alive || scheduleGeneration != generation) {
            scheduled = false
            return null
        }
        return latest.also {
            latest = null
            lastRenderMs = nowMs()
        }
    }

    @Synchronized
    private fun finishRender(scheduleGeneration: Int) {
        if (!alive || scheduleGeneration != generation) {
            scheduled = false
            return
        }
        if (latest != null) {
            scheduleLocked(delayMs = renderDelayLocked())
        } else {
            scheduled = false
        }
    }

    private fun renderDelayLocked(): Long {
        val lastRender = lastRenderMs ?: return 0L
        return (refreshIntervalMs - (nowMs() - lastRender)).coerceAtLeast(0L)
    }
}
