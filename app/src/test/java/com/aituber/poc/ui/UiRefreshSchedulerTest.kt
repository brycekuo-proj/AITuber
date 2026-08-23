package com.aituber.poc.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRefreshSchedulerTest {
    @Test
    fun highFrequencyUpdatesAreThrottled() {
        val harness = Harness()

        repeat(1000) { value ->
            harness.scheduler.submit(value)
        }
        harness.drainDue()

        assertEquals(listOf(999), harness.rendered)
        assertTrue(harness.postedCount < 10)
    }

    @Test
    fun latestValueWinsAcrossThrottleWindow() {
        val harness = Harness()

        harness.scheduler.submit(1)
        harness.drainDue()
        harness.scheduler.submit(2)
        harness.scheduler.submit(3)
        harness.advanceBy(150L)
        harness.drainDue()

        assertEquals(listOf(1, 3), harness.rendered)
    }

    @Test
    fun destroyInvalidatesQueuedRefresh() {
        val harness = Harness()

        harness.scheduler.submit(1)
        harness.scheduler.destroy()
        harness.drainDue()

        assertEquals(emptyList<Int>(), harness.rendered)
    }

    private class Harness {
        var nowMs = 1_000L
        val rendered = mutableListOf<Int>()
        private val posted = mutableListOf<ScheduledBlock>()
        val postedCount: Int
            get() = posted.size

        val scheduler = UiRefreshScheduler(
            postToUiDelayed = { block, delayMs -> posted.add(ScheduledBlock(nowMs + delayMs, block)) },
            nowMs = { nowMs },
            refreshIntervalMs = 150L,
            render = { value: Int -> rendered.add(value) }
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
}
