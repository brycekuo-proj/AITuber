package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngThinkingMotionTest {
    @Test
    fun thinkingEntrySequenceUsesPhoneApprovedCbaDedabcOrder() {
        assertEquals(
            listOf(
                StaticPngThinkingFrameId.C,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.C
            ),
            StaticPngThinkingMotion.ENTRY_SEQUENCE.map { it.frameId }
        )
    }

    @Test
    fun thinkingExitSequenceRestoresFromNeutralFrameC() {
        assertEquals(
            listOf(StaticPngThinkingFrameId.C),
            StaticPngThinkingMotion.EXIT_SEQUENCE.map { it.frameId }
        )
    }

    @Test
    fun debugLoopSequenceUsesExactPhoneApprovedCbaDedabcOrder() {
        assertEquals(
            listOf(
                StaticPngThinkingFrameId.C,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.C
            ),
            StaticPngThinkingMotion.DEBUG_LOOP_SEQUENCE.map { it.frameId }
        )
    }

    @Test
    fun thinkingTransitionModesExposeDirectCrossfadeAndBridge() {
        assertEquals(
            listOf(
                StaticPngThinkingTransitionMode.DIRECT,
                StaticPngThinkingTransitionMode.CROSSFADE,
                StaticPngThinkingTransitionMode.BRIDGE
            ),
            StaticPngThinkingTransitionMode.values().toList()
        )
    }

    @Test
    fun thinkingTransitionDurationsMatchPocDefaults() {
        assertEquals(0L, StaticPngThinkingMotion.transitionDurationMs(StaticPngThinkingTransitionMode.DIRECT))
        assertEquals(220L, StaticPngThinkingMotion.transitionDurationMs(StaticPngThinkingTransitionMode.CROSSFADE))
        assertEquals(320L, StaticPngThinkingMotion.transitionDurationMs(StaticPngThinkingTransitionMode.BRIDGE))
    }

    @Test
    fun thinkingRuntimeSpeedDefaultsToApprovedFrameHoldAndClampsSeekBarRange() {
        assertEquals(200L, StaticPngThinkingRuntimeTuning.FRAME_HOLD_MIN_MS)
        assertEquals(1_200L, StaticPngThinkingRuntimeTuning.FRAME_HOLD_MAX_MS)
        assertEquals(520L, StaticPngThinkingRuntimeTuning.DEFAULT_FRAME_HOLD_MS)

        StaticPngThinkingRuntimeTuning.setFrameHoldMs(1L)
        assertEquals(
            StaticPngThinkingRuntimeTuning.FRAME_HOLD_MIN_MS,
            StaticPngThinkingRuntimeTuning.frameHoldMs
        )
        StaticPngThinkingRuntimeTuning.setFrameHoldMs(9_999L)
        assertEquals(
            StaticPngThinkingRuntimeTuning.FRAME_HOLD_MAX_MS,
            StaticPngThinkingRuntimeTuning.frameHoldMs
        )
        StaticPngThinkingRuntimeTuning.setFrameHoldMs(430L)
        assertEquals(430L, StaticPngThinkingRuntimeTuning.frameHoldMs)

        StaticPngThinkingRuntimeTuning.setFrameHoldMs(
            StaticPngThinkingRuntimeTuning.DEFAULT_FRAME_HOLD_MS
        )
    }

    @Test
    fun loopStateStartSchedulesOnceUntilAdvanced() {
        val state = StaticPngThinkingLoopState()

        assertTrue(state.start())
        assertTrue(state.playbackEnabled)
        assertTrue(state.transitionScheduled)
        assertEquals(StaticPngThinkingFrameId.C, state.frameId)
        assertEquals(1, state.scheduleCount)

        assertFalse(state.start())
        assertEquals(1, state.scheduleCount)

        assertEquals(StaticPngThinkingFrameId.B, state.advance())
        assertFalse(state.transitionScheduled)
        assertEquals(StaticPngThinkingFrameId.B, state.frameId)

        assertTrue(state.scheduleNext())
        assertEquals(2, state.scheduleCount)
    }

    @Test
    fun nonLoopingEntryCompletesFullCbaDedabcCycleAndStopsAtFrameC() {
        val state = StaticPngThinkingLoopState()

        assertTrue(state.start(loop = false))
        val advanced = mutableListOf<StaticPngThinkingFrameId>()
        while (state.playbackEnabled) {
            state.advance()?.let(advanced::add)
            if (state.playbackEnabled) state.scheduleNext()
        }

        assertEquals(
            listOf(
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.C
            ),
            advanced
        )
        assertEquals(StaticPngThinkingFrameId.C, state.frameId)
    }

    @Test
    fun loopStateStopPreventsFurtherAdvanceAndReturnsToFrameC() {
        val state = StaticPngThinkingLoopState()

        assertTrue(state.start())
        assertEquals(StaticPngThinkingFrameId.B, state.advance())
        state.stop()

        assertFalse(state.playbackEnabled)
        assertFalse(state.transitionScheduled)
        assertEquals(StaticPngThinkingFrameId.C, state.frameId)
        assertFalse(state.scheduleNext())
        assertNull(state.advance())
    }

    @Test
    fun thinkingDoesNotChangeIdleMouthBlinkOrHairAssets() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__mouth__half__replacement__v5.png",
            characterPackage.mouthLayers.getValue(StaticPngMouthShape.HALF).assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__eyes__half__replacement__v3.png",
            characterPackage.eyeLayers.getValue(StaticPngEyeShape.HALF).assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__hair__float_a__replacement__v7.png",
            characterPackage.hairLayers.getValue(StaticPngHairShape.FLOAT_A).assetPath
        )
        assertEquals(StaticPngHairTransitionMode.CROSSFADE, StaticPngHairTransitionMode.CROSSFADE)
    }
}
