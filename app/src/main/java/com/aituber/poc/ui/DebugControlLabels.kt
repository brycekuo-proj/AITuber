package com.aituber.poc.ui

import com.aituber.poc.character.CharacterCapabilities
import com.aituber.poc.character.CharacterMode

object DebugControlLabels {
    const val TEST_BLINK = "TEST BLINK"

    fun capture(captureActive: Boolean): String =
        if (captureActive) "STOP CAPTURE" else "START CAPTURE"

    fun overlay(overlayEnabled: Boolean): String =
        if (overlayEnabled) "OVERLAY: ON" else "OVERLAY: OFF"

    fun character(mode: CharacterMode): String =
        character(mode, "Live2D")

    fun character(mode: CharacterMode, live2dProfileName: String): String = when (mode) {
        CharacterMode.MINIMAL_MOUTH -> "CHARACTER: MINIMAL"
        CharacterMode.LIVE2D -> "CHARACTER: ${live2dProfileName.uppercase()}"
        CharacterMode.STATE_VIDEO -> "CHARACTER: WHITEHAIR FEMALE"
    }

    fun diagnostics(expanded: Boolean): String =
        if (expanded) "HIDE DIAGNOSTICS" else "SHOW DIAGNOSTICS"

    fun live2dModel(profileName: String): String = "LIVE2D MODEL: ${profileName.uppercase()}"

    fun testBreath(parameterStatus: String): String =
        if (CharacterCapabilities.supportsBreath(parameterStatus)) {
            "TEST BREATH"
        } else {
            "TEST BREATH - UNSUPPORTED"
        }

    fun testBreathEnabled(parameterStatus: String): Boolean =
        CharacterCapabilities.supportsBreath(parameterStatus)

    fun testIdle(idleMotionCount: Int): String =
        if (idleMotionCount > 0) "TEST IDLE" else "TEST IDLE - UNSUPPORTED"

    fun testIdleEnabled(idleMotionCount: Int): Boolean = idleMotionCount > 0

    fun testPhysics(physicsLoaded: String): String =
        if (testPhysicsEnabled(physicsLoaded)) "TEST PHYSICS" else "TEST PHYSICS - UNSUPPORTED"

    fun testPhysicsEnabled(physicsLoaded: String): Boolean = physicsLoaded == "YES"

    fun testEars(fallbackIdleEnabled: String, earOutputsAvailable: String): String =
        if (testEarsEnabled(fallbackIdleEnabled, earOutputsAvailable)) "TEST EARS" else "TEST EARS - UNSUPPORTED"

    fun testEarsEnabled(fallbackIdleEnabled: String, earOutputsAvailable: String): Boolean =
        fallbackIdleEnabled == "YES" && earOutputsAvailable == "YES"

    fun stateVideoTestButton(stateName: String): String = "TEST $stateName"

    fun stateVideoControlsVisible(mode: CharacterMode): Boolean = mode == CharacterMode.STATE_VIDEO
}
