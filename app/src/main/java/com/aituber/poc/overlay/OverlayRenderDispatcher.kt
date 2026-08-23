package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class OverlayRenderDispatcher(
    private val postToMain: (() -> Unit) -> Unit,
    private val postToMainDelayed: (() -> Unit, Long) -> Unit = { block, _ -> postToMain(block) },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val renderIntervalMs: Long = 50L,
    private val isMainThread: () -> Boolean,
    private val trace: (String) -> Unit,
    private val renderOnMain: (UniversalStateSnapshot) -> Unit,
    private val startAnimationOnMain: () -> Unit,
    private val stopAnimationOnMain: () -> Unit
) {
    private var alive = true
    private var generation = 0
    private var renderedState = UniversalAiState.UNKNOWN
    private var pendingSnapshot: UniversalStateSnapshot? = null
    private var renderScheduled = false
    private var lastRenderMs: Long? = null

    @Synchronized
    fun onState(snapshot: UniversalStateSnapshot) {
        trace("state callback received thread=${Thread.currentThread().name}")
        pendingSnapshot = snapshot
        if (renderScheduled) return
        scheduleRenderLocked(delayMs = renderDelayLocked())
    }

    @Synchronized
    fun destroy() {
        alive = false
        generation += 1
        pendingSnapshot = null
        renderScheduled = false
    }

    @Synchronized
    private fun scheduleRenderLocked(delayMs: Long) {
        if (!alive) return
        renderScheduled = true
        val callbackGeneration = generation
        trace("state render posted")
        postToMainDelayed({
            val snapshot = takeSnapshotForRender(callbackGeneration) ?: return@postToMainDelayed
            trace("state rendered on main thread=${isMainThread()}")
            renderOnMain(snapshot)
            applyAnimationState(snapshot.state)
            finishRender(callbackGeneration)
        }, delayMs)
    }

    @Synchronized
    private fun takeSnapshotForRender(callbackGeneration: Int): UniversalStateSnapshot? {
        if (!alive || callbackGeneration != generation) {
            renderScheduled = false
            return null
        }
        return pendingSnapshot.also {
            pendingSnapshot = null
            lastRenderMs = nowMs()
        }
    }

    @Synchronized
    private fun finishRender(callbackGeneration: Int) {
        if (!alive || callbackGeneration != generation) {
            renderScheduled = false
            return
        }
        if (pendingSnapshot != null) {
            scheduleRenderLocked(delayMs = renderDelayLocked())
        } else {
            renderScheduled = false
        }
    }

    private fun renderDelayLocked(): Long {
        val lastRender = lastRenderMs ?: return 0L
        return (renderIntervalMs - (nowMs() - lastRender)).coerceAtLeast(0L)
    }

    private fun applyAnimationState(nextState: UniversalAiState) {
        if (nextState == UniversalAiState.SPEAKING) {
            if (renderedState != UniversalAiState.SPEAKING) {
                startAnimationOnMain()
            }
        } else if (renderedState == UniversalAiState.SPEAKING) {
            stopAnimationOnMain()
        } else {
            stopAnimationOnMain()
        }
        renderedState = nextState
    }
}
