package com.aituber.poc.character

object BreathDiagnostics {
    @Volatile
    private var current = BreathDiagnosticsSnapshot.empty()

    @Synchronized
    fun update(enabled: Boolean, frame: BreathFrame) {
        current = current.copy(
            enabled = if (enabled) "YES" else "NO",
            normalized = frame.normalized.toDouble(),
            cycleDurationMs = frame.cycleDurationMs,
            breathCount = frame.breathCount,
            testActive = if (frame.testActive) "YES" else "NO",
            testPhase = frame.testPhase,
            testIntensityPercent = frame.intensity.toDouble() * 100.0
        )
    }

    @Synchronized
    fun reset() {
        current = BreathDiagnosticsSnapshot.empty()
    }

    fun snapshot(): BreathDiagnosticsSnapshot = current
}

data class BreathDiagnosticsSnapshot(
    val enabled: String,
    val normalized: Double,
    val cycleDurationMs: Long,
    val breathCount: Long,
    val testActive: String,
    val testPhase: String,
    val testIntensityPercent: Double
) {
    companion object {
        fun empty() = BreathDiagnosticsSnapshot(
            enabled = "NO",
            normalized = 0.5,
            cycleDurationMs = 0L,
            breathCount = 0L,
            testActive = "NO",
            testPhase = "n/a",
            testIntensityPercent = 0.0
        )
    }
}
