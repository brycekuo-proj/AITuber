package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class OverlayRenderDispatcher(
    private val postToMain: (() -> Unit) -> Unit,
    private val isMainThread: () -> Boolean,
    private val trace: (String) -> Unit,
    private val renderOnMain: (UniversalStateSnapshot) -> Unit,
    private val startAnimationOnMain: () -> Unit,
    private val stopAnimationOnMain: () -> Unit
) {
    private var alive = true
    private var generation = 0
    private var renderedState = UniversalAiState.UNKNOWN

    fun onState(snapshot: UniversalStateSnapshot) {
        trace("state callback received thread=${Thread.currentThread().name}")
        val callbackGeneration = generation
        trace("state render posted")
        postToMain {
            if (!alive || callbackGeneration != generation) return@postToMain
            trace("state rendered on main thread=${isMainThread()}")
            renderOnMain(snapshot)
            applyAnimationState(snapshot.state)
        }
    }

    fun destroy() {
        alive = false
        generation += 1
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
