package com.aituber.poc.state

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class CaptureDiagnosticsAccumulator(
    private val nonZeroSampleThreshold: Float = 0.0005f
) {
    private var peakAudioLevel = 0f
    private var capturedSamples = 0L
    private var nonZeroSamples = 0L
    private var speakingEvents = 0
    private var lastNonZeroAudioElapsedMs: Long? = null
    private var lastReadResult: Int? = null
    private var lastState = UniversalAiState.UNKNOWN
    private var currentAudioLevel: Float? = null

    fun reset(): CaptureDiagnostics {
        peakAudioLevel = 0f
        capturedSamples = 0L
        nonZeroSamples = 0L
        speakingEvents = 0
        lastNonZeroAudioElapsedMs = null
        lastReadResult = null
        lastState = UniversalAiState.UNKNOWN
        currentAudioLevel = null
        return snapshot()
    }

    fun onReadResult(
        readResult: Int,
        buffer: ShortArray,
        length: Int,
        elapsedRealtimeMs: Long
    ): Float? {
        lastReadResult = readResult
        if (readResult <= 0 || length <= 0) {
            currentAudioLevel = null
            return null
        }

        capturedSamples += length.toLong()
        val level = rms(buffer, length)
        currentAudioLevel = level
        peakAudioLevel = max(peakAudioLevel, level)

        var nonZeroInRead = 0L
        for (index in 0 until length) {
            val normalized = abs(buffer[index] / Short.MAX_VALUE.toFloat())
            if (normalized > nonZeroSampleThreshold) {
                nonZeroInRead += 1
            }
        }
        if (nonZeroInRead > 0) {
            nonZeroSamples += nonZeroInRead
            lastNonZeroAudioElapsedMs = elapsedRealtimeMs
        }

        return level
    }

    fun onState(state: UniversalAiState): CaptureDiagnostics {
        if (lastState == UniversalAiState.IDLE && state == UniversalAiState.SPEAKING) {
            speakingEvents += 1
        }
        lastState = state
        return snapshot()
    }

    fun onReadError(errorCode: Int): CaptureDiagnostics {
        lastReadResult = errorCode
        currentAudioLevel = null
        return snapshot()
    }

    fun onNoSamples(readResult: Int): CaptureDiagnostics {
        lastReadResult = readResult
        currentAudioLevel = null
        return snapshot()
    }

    fun snapshot(): CaptureDiagnostics {
        val diagnostic = when {
            (lastReadResult ?: 0) < 0 -> CaptureDiagnosticStatus.AUDIO_RECORD_ERROR
            capturedSamples == 0L -> CaptureDiagnosticStatus.NO_SAMPLES
            currentAudioLevel != null && currentAudioLevel!! > nonZeroSampleThreshold -> CaptureDiagnosticStatus.RECEIVING_AUDIO
            else -> CaptureDiagnosticStatus.RECEIVING_SILENCE
        }

        return CaptureDiagnostics(
            currentAudioLevel = currentAudioLevel,
            peakAudioLevel = peakAudioLevel,
            capturedSamples = capturedSamples,
            nonZeroSamples = nonZeroSamples,
            speakingEvents = speakingEvents,
            lastNonZeroAudioElapsedMs = lastNonZeroAudioElapsedMs,
            lastReadResult = lastReadResult,
            diagnostic = diagnostic
        )
    }

    private fun rms(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (index in 0 until length) {
            val normalized = buffer[index] / Short.MAX_VALUE.toDouble()
            sum += normalized * normalized
        }
        return sqrt(sum / length).toFloat()
    }
}
