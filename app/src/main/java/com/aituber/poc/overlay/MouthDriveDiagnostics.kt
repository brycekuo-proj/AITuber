package com.aituber.poc.overlay

object MouthDriveDiagnostics {
    @Volatile
    private var current = MouthDriveSnapshot.empty()

    fun update(
        frame: MouthDriveFrame,
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
            mode = frame.mode.name,
            targetOpen = frame.targetOpen,
            smoothedOpen = frame.smoothedOpen,
            mouthCloseMode = frame.closeMode.name,
            mouthActiveCloseTimeConstantMs = frame.activeTimeConstantMs,
            silenceCloseStartTimeMs = frame.silenceCloseStartTimeMs,
            silenceCloseDurationMs = frame.silenceCloseDurationMs,
            mouthClosedSnapThreshold = frame.silenceSnapClosedThreshold,
            mouthClosedSnapCount = frame.closedSnapCount,
            lastClosedSnapTimeMs = frame.lastClosedSnapTimeMs,
            mouthRmsNormalized = frame.loudness.rmsNormalized,
            mouthPeakNormalized = frame.loudness.peakNormalized,
            mouthLoudnessBoosted = frame.loudness.boosted,
            mouthLoudnessContrast = frame.loudness.contrast,
            mouthLoudnessBand = frame.loudness.band.name,
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
    val mouthCloseMode: String,
    val mouthActiveCloseTimeConstantMs: Long,
    val silenceCloseStartTimeMs: Long?,
    val silenceCloseDurationMs: Long,
    val mouthClosedSnapThreshold: Double,
    val mouthClosedSnapCount: Int,
    val lastClosedSnapTimeMs: Long?,
    val mouthRmsNormalized: Double,
    val mouthPeakNormalized: Double,
    val mouthLoudnessBoosted: Double,
    val mouthLoudnessContrast: Double,
    val mouthLoudnessBand: String,
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
            mouthCloseMode = MouthCloseMode.CLOSED.name,
            mouthActiveCloseTimeConstantMs = 0L,
            silenceCloseStartTimeMs = null,
            silenceCloseDurationMs = 0L,
            mouthClosedSnapThreshold = MouthAmplitudeMapper.Config().silenceSnapClosedThreshold,
            mouthClosedSnapCount = 0,
            lastClosedSnapTimeMs = null,
            mouthRmsNormalized = 0.0,
            mouthPeakNormalized = 0.0,
            mouthLoudnessBoosted = 0.0,
            mouthLoudnessContrast = 0.0,
            mouthLoudnessBand = MouthLoudnessBand.QUIET.name,
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
