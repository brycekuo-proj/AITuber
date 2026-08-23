package com.aituber.poc.overlay

object MouthDriveDiagnostics {
    @Volatile
    private var current = MouthDriveSnapshot.empty()

    fun update(
        mode: MouthDriveMode,
        targetOpen: Double?,
        smoothedOpen: Double,
        gateFrame: MouthSilenceGateFrame = MouthSilenceGateFrame(
            mouthActive = false,
            audible = false,
            gateState = MouthGateState.SILENT,
            lastAudibleTimeMs = null,
            silenceDurationMs = 0L,
            silenceHoldRemainingMs = 0L,
            rms = 0.0,
            peak = 0.0,
            activityRatio = 0.0,
            timestampMs = 0L
        )
    ) {
        current = MouthDriveSnapshot(
            mode = mode.name,
            targetOpen = targetOpen,
            smoothedOpen = smoothedOpen,
            mouthAudible = if (gateFrame.audible) "YES" else "NO",
            mouthGateState = gateFrame.gateState.name,
            mouthLastAudibleTimeMs = gateFrame.lastAudibleTimeMs,
            mouthSilenceDurationMs = if (gateFrame.silenceDurationMs == Long.MAX_VALUE) null else gateFrame.silenceDurationMs,
            mouthSilenceHoldRemainingMs = gateFrame.silenceHoldRemainingMs,
            mouthGateRms = gateFrame.rms,
            mouthGatePeak = gateFrame.peak,
            mouthGateActivityRatio = gateFrame.activityRatio
        )
    }

    fun reset() {
        current = MouthDriveSnapshot.empty()
    }

    fun snapshot(): MouthDriveSnapshot = current
}

data class MouthDriveSnapshot(
    val mode: String,
    val targetOpen: Double?,
    val smoothedOpen: Double,
    val mouthAudible: String,
    val mouthGateState: String,
    val mouthLastAudibleTimeMs: Long?,
    val mouthSilenceDurationMs: Long?,
    val mouthSilenceHoldRemainingMs: Long,
    val mouthGateRms: Double,
    val mouthGatePeak: Double,
    val mouthGateActivityRatio: Double
) {
    companion object {
        fun empty() = MouthDriveSnapshot(
            mode = MouthDriveMode.CLOSED.name,
            targetOpen = 0.0,
            smoothedOpen = 0.0,
            mouthAudible = "NO",
            mouthGateState = MouthGateState.SILENT.name,
            mouthLastAudibleTimeMs = null,
            mouthSilenceDurationMs = null,
            mouthSilenceHoldRemainingMs = 0L,
            mouthGateRms = 0.0,
            mouthGatePeak = 0.0,
            mouthGateActivityRatio = 0.0
        )
    }
}
