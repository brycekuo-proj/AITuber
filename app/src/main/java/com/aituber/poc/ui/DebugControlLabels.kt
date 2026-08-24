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
        if (mode == CharacterMode.LIVE2D) "CHARACTER: LIVE2D" else "CHARACTER: MINIMAL"

    fun diagnostics(expanded: Boolean): String =
        if (expanded) "HIDE DIAGNOSTICS" else "SHOW DIAGNOSTICS"

    fun testBreath(parameterStatus: String): String =
        if (CharacterCapabilities.supportsBreath(parameterStatus)) {
            "TEST BREATH"
        } else {
            "TEST BREATH - UNSUPPORTED"
        }

    fun testBreathEnabled(parameterStatus: String): Boolean =
        CharacterCapabilities.supportsBreath(parameterStatus)
}
