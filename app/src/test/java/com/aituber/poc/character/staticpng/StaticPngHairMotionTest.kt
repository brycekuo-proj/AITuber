package com.aituber.poc.character.staticpng

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticPngHairMotionTest {
    @Test
    fun hairMotionSequenceAlternatesThroughBaseAAndB() {
        assertEquals(
            listOf(
                StaticPngHairShape.BASE,
                StaticPngHairShape.FLOAT_A,
                StaticPngHairShape.BASE,
                StaticPngHairShape.FLOAT_B,
                StaticPngHairShape.BASE
            ),
            StaticPngHairMotion.SEQUENCE.map { it.shape }
        )
    }

    @Test
    fun hairMotionCycleIsInPocRangeAndDoesNotSyncWithIdleMotion() {
        assertTrue(StaticPngHairMotion.CYCLE_MS in 3_200L..4_800L)
        assertNotEquals(StaticPngIdleMotion.PERIOD_MS, StaticPngHairMotion.CYCLE_MS)
        assertTrue(StaticPngHairMotion.TRANSITION_MS in 0L..300L)
    }

    @Test
    fun hairTransitionModesExposeDirectCrossfadeAndBridge() {
        assertEquals(
            listOf(
                StaticPngHairTransitionMode.DIRECT,
                StaticPngHairTransitionMode.CROSSFADE,
                StaticPngHairTransitionMode.BRIDGE
            ),
            StaticPngHairTransitionMode.values().toList()
        )
    }

    @Test
    fun hairTransitionTimingsMatchPocRange() {
        assertTrue(StaticPngHairMotion.CROSSFADE_MS in 100L..140L)
        assertEquals(60L, StaticPngHairMotion.BRIDGE_TO_BASE_MS)
        assertTrue(StaticPngHairMotion.BRIDGE_BASE_HOLD_MS in 20L..40L)
        assertTrue(StaticPngHairMotion.BRIDGE_FROM_BASE_MS in 80L..120L)
    }

    @Test
    fun loopStateStartSchedulesOnceUntilTransitionApplied() {
        val loopState = StaticPngHairLoopState()

        assertTrue(loopState.start())
        assertTrue(loopState.motionEnabled)
        assertTrue(loopState.transitionScheduled)
        assertEquals(StaticPngHairShape.BASE, loopState.shape)
        assertEquals(1, loopState.scheduleCount)

        assertFalse(loopState.start())
        assertEquals(1, loopState.scheduleCount)

        loopState.apply(StaticPngHairShape.FLOAT_A)
        assertFalse(loopState.transitionScheduled)
        assertEquals(StaticPngHairShape.FLOAT_A, loopState.shape)

        assertTrue(loopState.scheduleNext())
        assertEquals(2, loopState.scheduleCount)
    }

    @Test
    fun loopStateStopResetsToBaseAndPreventsTransitions() {
        val loopState = StaticPngHairLoopState()

        assertTrue(loopState.start())
        loopState.apply(StaticPngHairShape.FLOAT_B)
        loopState.stop()

        assertFalse(loopState.motionEnabled)
        assertFalse(loopState.transitionScheduled)
        assertEquals(StaticPngHairShape.BASE, loopState.shape)
        assertFalse(loopState.scheduleNext())
        loopState.apply(StaticPngHairShape.FLOAT_A)
        assertEquals(StaticPngHairShape.BASE, loopState.shape)
    }

    @Test
    fun hairAssetsDoNotChangeMouthEyeOrIdleConfiguration() {
        val characterPackage = StaticPngCharacterPackage.XianxiaFemale

        assertEquals(
            "characters/xianxia_female/static/xianxia_female__hair__float_a__replacement__v6.png",
            characterPackage.hairLayers.getValue(StaticPngHairShape.FLOAT_A).assetPath
        )
        assertEquals(4_200L, StaticPngIdleMotion.PERIOD_MS)
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__eyes__half__replacement__v3.png",
            characterPackage.eyeLayers.getValue(StaticPngEyeShape.HALF).assetPath
        )
        assertEquals(
            "characters/xianxia_female/static/xianxia_female__mouth__half__replacement__v5.png",
            characterPackage.mouthLayers.getValue(StaticPngMouthShape.HALF).assetPath
        )
        assertEquals(2f, StaticPngIdleMotion.OFFSET_Y_DP)
        assertEquals(0.004f, StaticPngIdleMotion.SCALE_AMPLITUDE)
    }
}
