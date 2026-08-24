package com.aituber.poc.character

object BlinkDiagnostics {
    @Volatile
    private var current = BlinkDiagnosticsSnapshot.empty()

    @Synchronized
    fun update(enabled: Boolean, frame: BlinkFrame) {
        current = current.copy(
            enabled = if (enabled) "YES" else "NO",
            state = frame.state.name,
            eyeLeftOpen = frame.leftEyeOpen.toDouble(),
            eyeRightOpen = frame.rightEyeOpen.toDouble(),
            nextBlinkInMs = frame.nextBlinkInMs,
            blinkCount = frame.blinkCount
        )
    }

    @Synchronized
    fun reset() {
        current = BlinkDiagnosticsSnapshot.empty()
    }

    fun snapshot(): BlinkDiagnosticsSnapshot = current
}

data class BlinkDiagnosticsSnapshot(
    val enabled: String,
    val state: String,
    val eyeLeftOpen: Double,
    val eyeRightOpen: Double,
    val nextBlinkInMs: Long,
    val blinkCount: Long
) {
    companion object {
        fun empty() = BlinkDiagnosticsSnapshot(
            enabled = "NO",
            state = BlinkState.OPEN.name,
            eyeLeftOpen = 1.0,
            eyeRightOpen = 1.0,
            nextBlinkInMs = 0L,
            blinkCount = 0L
        )
    }
}
