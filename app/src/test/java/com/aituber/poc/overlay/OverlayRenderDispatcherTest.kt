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
        assertEquals(1, harness.postedCount)
        assertTrue(harness.trace.any { it.startsWith("state callback received thread=") })
        assertTrue(harness.trace.contains("state render posted"))
    }

    @Test
    fun renderDispatchRunsOnMainQueue() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.drainDue()

        assertEquals(listOf(UniversalAiState.SPEAKING), harness.renderedStates)
        assertEquals(1, harness.startAnimationCount)
        assertTrue(harness.trace.contains("state rendered on main thread=true"))
    }

    @Test
    fun destroyAfterQueuedRenderPreventsRender() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.dispatcher.destroy()
        harness.drainDue()

        assertEquals(0, harness.renderedStates.size)
        assertEquals(0, harness.startAnimationCount)
    }

    @Test
    fun repeatedSpeakingUpdatesAreCoalescedAndDoNotStartMultipleAnimationLoops() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.drainDue()

        assertEquals(1, harness.renderedStates.size)
        assertEquals(1, harness.startAnimationCount)
    }

    @Test
    fun idleStopsSpeakingAnimation() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.drainDue()
        harness.dispatcher.onState(snapshot(UniversalAiState.IDLE))
        harness.advanceBy(50L)
        harness.drainDue()

        assertEquals(
            listOf(UniversalAiState.SPEAKING, UniversalAiState.IDLE),
            harness.renderedStates
        )
        assertEquals(1, harness.startAnimationCount)
        assertEquals(1, harness.stopAnimationCount)
    }

    @Test
    fun rapidUpdatesDoNotProduceOneRenderPerSnapshot() {
        val harness = DispatcherHarness()

        repeat(1000) {
            harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        }
        harness.drainDue()

        assertEquals(1, harness.renderedStates.size)
        assertTrue(harness.postedCount < 10)
    }

    @Test
    fun latestSnapshotWins() {
        val harness = DispatcherHarness()

        harness.dispatcher.onState(snapshot(UniversalAiState.IDLE))
        harness.dispatcher.onState(snapshot(UniversalAiState.SPEAKING))
        harness.dispatcher.onState(snapshot(UniversalAiState.UNKNOWN))
        harness.drainDue()

        assertEquals(listOf(UniversalAiState.UNKNOWN), harness.renderedStates)
    }

    private class DispatcherHarness {
        private val posted = mutableListOf<ScheduledBlock>()
        val trace = mutableListOf<String>()
        val renderedStates = mutableListOf<UniversalAiState>()
        var startAnimationCount = 0
        var stopAnimationCount = 0
        var nowMs = 1_000L
        val postedCount: Int
            get() = posted.size

        val dispatcher = OverlayRenderDispatcher(
            postToMain = { block -> posted.add(ScheduledBlock(nowMs, block)) },
            postToMainDelayed = { block, delayMs -> posted.add(ScheduledBlock(nowMs + delayMs, block)) },
            nowMs = { nowMs },
            isMainThread = { true },
            trace = { step -> trace.add(step) },
            renderOnMain = { snapshot -> renderedStates.add(snapshot.state) },
            startAnimationOnMain = { startAnimationCount += 1 },
            stopAnimationOnMain = { stopAnimationCount += 1 }
        )

        fun advanceBy(deltaMs: Long) {
            nowMs += deltaMs
        }

        fun drainDue() {
            val pending = posted.filter { it.runAtMs <= nowMs }
            posted.removeAll(pending)
            pending.forEach { it.block.invoke() }
        }
    }

    private data class ScheduledBlock(
        val runAtMs: Long,
        val block: () -> Unit
    )

    private fun snapshot(state: UniversalAiState) = UniversalStateSnapshot(
        targetApp = "ChatGPT (com.openai.chatgpt)",
        detectionMethod = "test",
        state = state,
        audioLevel = null,
        captureStatus = "test"
    )
}
