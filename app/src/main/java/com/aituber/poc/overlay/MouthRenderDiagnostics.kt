package com.aituber.poc.overlay

import android.os.SystemClock
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics

object MouthRenderDiagnostics {
    @Volatile
    private var current = MouthRenderSnapshot.empty()

    @Synchronized
    fun reset() {
        current = MouthRenderSnapshot.empty()
    }

    @Synchronized
    fun recordMapper(
        mode: MouthDriveMode,
        targetOpen: Double?,
        smoothedOpen: Double
    ) {
        current = current.copy(
            mouthDriveMode = mode.name,
            mapperTargetOpen = targetOpen,
            smoothedOpen = smoothedOpen,
            lastRenderTimestampMs = nowMs(),
            renderThread = Thread.currentThread().name
        )
    }

    @Synchronized
    fun recordOverlaySnapshot(
        state: UniversalAiState,
        metrics: VisualizerWaveformMetrics,
        targetOpen: Double?,
        smoothedOpen: Double
    ) {
        current = current.copy(
            overlayReceivedState = state.name,
            overlayReceivedRms = metrics.rms,
            overlayReceivedPeak = metrics.peak,
            mapperTargetOpen = targetOpen,
            smoothedOpen = smoothedOpen,
            lastRenderTimestampMs = nowMs(),
            renderThread = Thread.currentThread().name
        )
    }

    @Synchronized
    fun recordCharacterEngineRender() {
        current = current.copy(
            characterEngineRenderCount = current.characterEngineRenderCount + 1,
            lastRenderTimestampMs = nowMs(),
            renderThread = Thread.currentThread().name
        )
    }

    @Synchronized
    fun recordAdapterRender(speaking: Boolean, lastRatio: Float?) {
        current = current.copy(
            adapterRenderCount = current.adapterRenderCount + 1,
            adapterLastState = if (speaking) UniversalAiState.SPEAKING.name else UniversalAiState.IDLE.name,
            adapterLastMouthRatio = lastRatio?.toDouble(),
            lastRenderTimestampMs = nowMs(),
            renderThread = Thread.currentThread().name
        )
    }

    @Synchronized
    fun recordViewRequestedRatio(ratio: Float) {
        current = current.copy(
            viewSetMouthOpenRatioCount = current.viewSetMouthOpenRatioCount + 1,
            viewLastRequestedRatio = ratio.toDouble(),
            lastRenderTimestampMs = nowMs(),
            renderThread = Thread.currentThread().name
        )
    }

    @Synchronized
    fun recordDraw(
        state: UniversalAiState,
        ratio: Float,
        width: Int,
        height: Int,
        calculatedMouthHeight: Float
    ) {
        current = current.copy(
            viewOnDrawCount = current.viewOnDrawCount + 1,
            viewLastDrawnState = state.name,
            viewLastDrawnRatio = ratio.toDouble(),
            viewWidth = width,
            viewHeight = height,
            calculatedMouthHeight = calculatedMouthHeight.toDouble(),
            lastDrawTimestampMs = nowMs(),
            drawThread = Thread.currentThread().name
        )
    }

    fun snapshot(): MouthRenderSnapshot = current

    private fun nowMs(): Long {
        return runCatching { SystemClock.elapsedRealtime() }.getOrElse { System.currentTimeMillis() }
    }
}

data class MouthRenderSnapshot(
    val mouthDriveMode: String,
    val mapperTargetOpen: Double?,
    val smoothedOpen: Double,
    val overlayReceivedState: String,
    val overlayReceivedRms: Double,
    val overlayReceivedPeak: Double,
    val characterEngineRenderCount: Long,
    val adapterRenderCount: Long,
    val adapterLastState: String,
    val adapterLastMouthRatio: Double?,
    val viewSetMouthOpenRatioCount: Long,
    val viewLastRequestedRatio: Double,
    val viewOnDrawCount: Long,
    val viewLastDrawnState: String,
    val viewLastDrawnRatio: Double,
    val viewWidth: Int,
    val viewHeight: Int,
    val calculatedMouthHeight: Double,
    val lastRenderTimestampMs: Long?,
    val lastDrawTimestampMs: Long?,
    val renderThread: String,
    val drawThread: String
) {
    companion object {
        fun empty() = MouthRenderSnapshot(
            mouthDriveMode = MouthDriveMode.CLOSED.name,
            mapperTargetOpen = 0.0,
            smoothedOpen = 0.0,
            overlayReceivedState = UniversalAiState.UNKNOWN.name,
            overlayReceivedRms = 0.0,
            overlayReceivedPeak = 0.0,
            characterEngineRenderCount = 0L,
            adapterRenderCount = 0L,
            adapterLastState = UniversalAiState.UNKNOWN.name,
            adapterLastMouthRatio = null,
            viewSetMouthOpenRatioCount = 0L,
            viewLastRequestedRatio = 0.0,
            viewOnDrawCount = 0L,
            viewLastDrawnState = UniversalAiState.UNKNOWN.name,
            viewLastDrawnRatio = 0.0,
            viewWidth = 0,
            viewHeight = 0,
            calculatedMouthHeight = 0.0,
            lastRenderTimestampMs = null,
            lastDrawTimestampMs = null,
            renderThread = "n/a",
            drawThread = "n/a"
        )
    }
}

object MouthDrawMetrics {
    fun calculatedMouthHeight(state: UniversalAiState, ratio: Float, height: Float): Float {
        val closedHeight = height * 0.12f
        val openHeight = height * 0.72f
        return if (state == UniversalAiState.SPEAKING) {
            closedHeight + (openHeight - closedHeight) * ratio.coerceIn(0f, 1f)
        } else {
            closedHeight
        }
    }
}
