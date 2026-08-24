package com.aituber.poc.character.live2d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DIdleMotionCatalogTest {
    @Test
    fun haruIdleGroupIsKnown() {
        assertEquals("Idle", Live2DIdleMotionCatalog.HARU_IDLE_GROUP)
    }

    @Test
    fun haruIdleMotionAssetMappingUsesOfficialModelReferences() {
        assertEquals(
            listOf(
                "motions/haru_g_idle.motion3.json",
                "motions/haru_g_m15.motion3.json"
            ),
            Live2DIdleMotionCatalog.haruIdleMotionFiles
        )
    }

    @Test
    fun haruIdleMotionMappingIsSmallAndDeterministic() {
        assertEquals(2, Live2DIdleMotionCatalog.haruIdleMotionFiles.size)
        assertTrue(Live2DIdleMotionCatalog.haruIdleMotionFiles.all { it.startsWith("motions/") })
        assertEquals(Live2DIdleMotionCatalog.haruIdleMotionFiles, Live2DIdleMotionCatalog.haruIdleMotionFiles.distinct())
    }
}
