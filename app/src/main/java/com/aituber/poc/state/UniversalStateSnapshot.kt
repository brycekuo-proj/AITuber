package com.aituber.poc.state

data class UniversalStateSnapshot(
    val targetApp: String,
    val detectionMethod: String,
    val state: UniversalAiState,
    val audioLevel: Float?,
    val captureStatus: String,
    val speakingSignalSource: String = "n/a",
    val diagnostics: CaptureDiagnostics = CaptureDiagnostics.empty(),
    val playbackProbe: PlaybackProbeSnapshot = PlaybackProbeSnapshot.empty(),
    val accessibilityProbe: AccessibilityProbeSnapshot = AccessibilityProbeSnapshot.empty(),
    val visualMotion: VisualMotionSnapshot = VisualMotionSnapshot.empty(),
    val visualizerProbe: VisualizerProbeSnapshot = VisualizerProbeSnapshot.empty(),
    val captureStartupTrace: CaptureStartupTraceSnapshot = CaptureStartupTraceSnapshot.empty()
)
