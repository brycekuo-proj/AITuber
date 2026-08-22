package com.aituber.poc.state

data class CaptureDiagnostics(
    val currentAudioLevel: Float?,
    val peakAudioLevel: Float,
    val capturedSamples: Long,
    val nonZeroSamples: Long,
    val speakingEvents: Int,
    val lastNonZeroAudioElapsedMs: Long?,
    val lastReadResult: Int?,
    val diagnostic: CaptureDiagnosticStatus
) {
    companion object {
        fun empty() = CaptureDiagnostics(
            currentAudioLevel = null,
            peakAudioLevel = 0f,
            capturedSamples = 0L,
            nonZeroSamples = 0L,
            speakingEvents = 0,
            lastNonZeroAudioElapsedMs = null,
            lastReadResult = null,
            diagnostic = CaptureDiagnosticStatus.NO_SAMPLES
        )
    }
}
