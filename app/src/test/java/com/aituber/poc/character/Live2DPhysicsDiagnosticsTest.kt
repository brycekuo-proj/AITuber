package com.aituber.poc.character

import com.aituber.poc.character.live2d.Live2DPhysicsAssets
import com.aituber.poc.character.live2d.Live2DPhysicsTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Live2DPhysicsDiagnosticsTest {
    @Test
    fun haruPhysicsReferenceIsRepresented() {
        assertEquals("Haru.physics3.json", Live2DPhysicsAssets.HARU_PHYSICS_FILE)
        assertEquals(14, Live2DPhysicsAssets.HARU_TOTAL_INPUT_COUNT)
        assertEquals(4, Live2DPhysicsAssets.HARU_TOTAL_OUTPUT_COUNT)
        assertEquals(
            listOf("ParamAngleX", "ParamAngleZ", "ParamBodyAngleX", "ParamBodyAngleZ"),
            Live2DPhysicsAssets.HARU_INPUT_PARAMETER_IDS
        )
        assertEquals(
            listOf("ParamHairFront", "ParamHairSide", "ParamHairBack", "ParamScarf"),
            Live2DPhysicsAssets.HARU_OUTPUT_PARAMETER_IDS
        )
    }

    @Test
    fun physicsLoadedDiagnosticsAreRecorded() {
        CharacterDiagnostics.recordLive2D(
            Live2DDiagnosticsSnapshot(
                available = true,
                runtimeLoaded = true,
                coreLoaded = true,
                modelLoaded = true,
                modelName = "Haru",
                mouthParameterId = "ParamMouthOpenY",
                mouthParameterValue = 0.7f,
                leftEyeParameterStatus = "APPLIED",
                rightEyeParameterStatus = "APPLIED",
                renderFps = 30.0,
                poseFile = "Haru.pose3.json",
                poseLoaded = true,
                poseActive = true,
                idleMotionEnabled = true,
                idleMotionStatus = "PLAYING",
                idleMotionCount = 2,
                idleMotionPlaying = true,
                physicsEnabled = true,
                physicsStatus = "ACTIVE",
                physicsFile = Live2DPhysicsAssets.HARU_PHYSICS_FILE,
                physicsLoaded = true,
                physicsUpdateCount = 12L,
                physicsLastDeltaMs = 33.3f,
                physicsInputCount = Live2DPhysicsAssets.HARU_TOTAL_INPUT_COUNT,
                physicsOutputCount = Live2DPhysicsAssets.HARU_TOTAL_OUTPUT_COUNT,
                physicsOutputParameterIds = "[ParamHairFront,ParamHairSide,ParamHairBack,ParamScarf]",
                lastPhysicsError = "n/a",
                fallbackReason = "n/a",
                mouthParameterStatus = "APPLIED"
            )
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals("YES", snapshot.live2dPhysicsEnabled)
        assertEquals("ACTIVE", snapshot.live2dPhysicsStatus)
        assertEquals("Haru.physics3.json", snapshot.live2dPhysicsFile)
        assertEquals("YES", snapshot.live2dPhysicsLoaded)
        assertEquals(12L, snapshot.live2dPhysicsUpdateCount)
        assertEquals(33.3, snapshot.live2dPhysicsLastDeltaMs, 0.01)
        assertEquals(14, snapshot.live2dPhysicsInputCount)
        assertEquals(4, snapshot.live2dPhysicsOutputCount)
        assertTrue(snapshot.live2dPhysicsOutputParameterIds.contains("ParamScarf"))
        assertEquals("n/a", snapshot.live2dLastPhysicsError)
    }

    @Test
    fun missingPhysicsDoesNotRequireLive2dFallback() {
        CharacterDiagnostics.recordLive2D(
            Live2DDiagnosticsSnapshot(
                available = true,
                runtimeLoaded = true,
                coreLoaded = true,
                modelLoaded = true,
                modelName = "Haru",
                mouthParameterId = "ParamMouthOpenY",
                mouthParameterValue = 0.4f,
                leftEyeParameterStatus = "APPLIED",
                rightEyeParameterStatus = "APPLIED",
                renderFps = 30.0,
                idleMotionEnabled = true,
                idleMotionStatus = "PLAYING",
                idleMotionCount = 2,
                idleMotionPlaying = true,
                physicsEnabled = false,
                physicsStatus = "PHYSICS_NOT_FOUND",
                physicsFile = "Haru.physics3.json",
                physicsLoaded = false,
                lastPhysicsError = "Asset not found",
                lifecycleState = "READY",
                fallbackReason = "n/a",
                mouthParameterStatus = "APPLIED"
            )
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals("YES", snapshot.live2dModelLoaded)
        assertEquals("PHYSICS_NOT_FOUND", snapshot.live2dPhysicsStatus)
        assertEquals("NO", snapshot.live2dPhysicsEnabled)
        assertEquals("Asset not found", snapshot.live2dLastPhysicsError)
        assertEquals("n/a", snapshot.live2dFallbackReason)
        assertEquals("APPLIED", snapshot.live2dMouthParameterStatus)
    }

    @Test
    fun physicsDeltaIsBoundedForPauseResume() {
        assertEquals(0f, Live2DPhysicsTiming.boundedDeltaMs(-5f), 0.0f)
        assertEquals(33f, Live2DPhysicsTiming.boundedDeltaMs(33f), 0.0f)
        assertEquals(100f, Live2DPhysicsTiming.boundedDeltaMs(500f), 0.0f)
    }
}
