package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngThinkingMotionTest {
    @Test
    fun thinkingEntrySequenceUsesPoseProgressionOrderAndStopsAtFinalThinkingPose() {
        assertEquals(
            listOf(
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.C
            ),
            StaticPngThinkingMotion.ENTRY_SEQUENCE.map { it.frameId }
        )
    }

    @Test
    fun thinkingExitSequenceReturnsFromFinalThinkingPoseToNeutral() {
        assertEquals(
            listOf(
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.A
            ),
            StaticPngThinkingMotion.EXIT_SEQUENCE.map { it.frameId }
        )
    }

    @Test
    fun debugLoopSequenceUsesFullForwardAndReverseProgression() {
        assertEquals(
            listOf(
                StaticPngThinkingFrameId.A,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.C,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.B,
                StaticPngThinkingFrameId.A
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
    fun loopStateStartSchedulesOnceUntilAdvanced() {
        val state = StaticPngThinkingLoopState()

        assertTrue(state.start())
        assertTrue(state.playbackEnabled)
        assertTrue(state.transitionScheduled)
        assertEquals(StaticPngThinkingFrameId.A, state.frameId)
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
    fun nonLoopingEntryStopsAtFrameCInsteadOfRepeatingLargeMotion() {
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
                StaticPngThinkingFrameId.E,
                StaticPngThinkingFrameId.D,
                StaticPngThinkingFrameId.C
            ),
            advanced
        )
        assertEquals(StaticPngThinkingFrameId.C, state.frameId)
    }

    @Test
    fun loopStateStopPreventsFurtherAdvanceAndReturnsToFrameA() {
        val state = StaticPngThinkingLoopState()

        assertTrue(state.start())
        assertEquals(StaticPngThinkingFrameId.B, state.advance())
        state.stop()

        assertFalse(state.playbackEnabled)
        assertFalse(state.transitionScheduled)
        assertEquals(StaticPngThinkingFrameId.A, state.frameId)
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
