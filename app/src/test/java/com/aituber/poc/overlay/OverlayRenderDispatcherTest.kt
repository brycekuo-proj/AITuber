package com.aituber.poc.overlay

import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRenderDispatcherTest {
    @Test
    fun backgroundThreadStateUpdateDoesNotRenderDirectly() {
        val harness = DispatcherHarness()
        val callbackFinished = CountDownLatch(1)

        Thread {
            harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
            callbackFinished.countDown()
        }.start()
        callbackFinished.await()

        assertEquals(0, harness.renderedStates.size)
        assertEquals(1, harness.posted.size)
        assertTrue(harness.trace.any { it.startsWith("state callback received thread=") })
        assertTrue(harness.trace.contains("state render posted"))
    }

    @Test
    fun renderDispatchRunsOnMainQueue() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.drainPosted()

        assertEquals(listOf(UniversalAiState.SPEAKING), harness.renderedStates)
        assertEquals(1, harness.startAnimationCount)
        assertTrue(harness.trace.contains("state rendered on main thread=true"))
    }

    @Test
    fun destroyAfterQueuedRenderPreventsRender() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.dispatcher.destroy()
        harness.drainPosted()

        assertEquals(0, harness.renderedStates.size)
        assertEquals(0, harness.startAnimationCount)
    }

    @Test
    fun repeatedSpeakingUpdatesDoNotStartMultipleAnimationLoops() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.drainPosted()

        assertEquals(2, harness.renderedStates.size)
        assertEquals(1, harness.startAnimationCount)
    }

    @Test
    fun idleStopsSpeakingAnimation() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.drainPosted()
        harness.dispatcher.onState(snapshot(UniversalAiState.IDLE))
        harness.drainPosted()

        assertEquals(
            listOf(UniversalAiState.SPEAKING, UniversalAiState.IDLE),
            harness.renderedStates
        )
        assertEquals(1, harness.startAnimationCount)
        assertEquals(1, harness.stopAnimationCount)
    }

    private class DispatcherHarness {
        val posted = mutableListOf<() -> Unit>()
        val trace = mutableListOf<String>()
        val renderedStates = mutableListOf<UniversalAiState>()
        var startAnimationCount = 0
        var stopAnimationCount = 0

        val dispatcher = OverlayRenderDispatcher(
            postToMain = { block -> posted.add(block) },
            isMainThread = { true },
            trace = { step -> trace.add(step) },
            renderOnMain = { snapshot -> renderedStates.add(snapshot.state) },
            startAnimationOnMain = { startAnimationCount += 1 },
            stopAnimationOnMain = { stopAnimationCount += 1 }
        )

        fun drainPosted() {
            val pending = posted.toList()
            posted.clear()
            pending.forEach { it.invoke() }
        }
    }

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
