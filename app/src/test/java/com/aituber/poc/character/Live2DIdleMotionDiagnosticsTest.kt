package com.aituber.poc.character

import org.junit.Assert.assertEquals
import org.junit.Test

class Live2DIdleMotionDiagnosticsTest {
    @Test
    fun idleMotionPlayingDiagnosticsAreRecorded() {
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
                idleMotionGroup = "Idle",
                idleMotionFile = "motions/haru_g_idle.motion3.json",
                idleMotionIndex = 0,
                idleMotionPlaying = true,
                idleMotionCount = 2,
                idleMotionPlayCount = 1L,
                lastIdleMotionError = "n/a",
                fallbackReason = "n/a",
                mouthParameterStatus = "APPLIED"
            )
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals("YES", snapshot.live2dPoseActive)
        assertEquals("YES", snapshot.live2dIdleMotionEnabled)
        assertEquals("PLAYING", snapshot.live2dIdleMotionStatus)
        assertEquals("Idle", snapshot.live2dIdleMotionGroup)
        assertEquals("motions/haru_g_idle.motion3.json", snapshot.live2dIdleMotionFile)
        assertEquals(0, snapshot.live2dIdleMotionIndex)
        assertEquals("YES", snapshot.live2dIdleMotionPlaying)
        assertEquals(2, snapshot.live2dIdleMotionCount)
        assertEquals(1L, snapshot.live2dIdleMotionPlayCount)
    }

    @Test
    fun missingIdleMotionDoesNotRequireLive2dFallback() {
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
                idleMotionEnabled = false,
                idleMotionStatus = "FAILED",
                idleMotionGroup = "Idle",
                idleMotionFile = "motions/missing.motion3.json",
                idleMotionIndex = 0,
                idleMotionPlaying = false,
                idleMotionCount = 2,
                idleMotionPlayCount = 0L,
                lastIdleMotionError = "Asset not found",
                lifecycleState = "READY",
                fallbackReason = "n/a",
                mouthParameterStatus = "APPLIED"
            )
        )

        val snapshot = CharacterDiagnostics.snapshot()

        assertEquals("YES", snapshot.live2dModelLoaded)
        assertEquals("FAILED", snapshot.live2dIdleMotionStatus)
        assertEquals("NO", snapshot.live2dIdleMotionEnabled)
        assertEquals("Asset not found", snapshot.live2dLastIdleMotionError)
        assertEquals("n/a", snapshot.live2dFallbackReason)
        assertEquals("APPLIED", snapshot.live2dMouthParameterStatus)
    }
}
