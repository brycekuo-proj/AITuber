package com.aituber.poc.state

data class UniversalStateSnapshot(
    val targetApp: String,
    val detectionMethod: String,
    val state: UniversalAiState,
    val audioLevel: Float?,
    val captureStatus: String,
    val diagnostics: CaptureDiagnostics = CaptureDiagnostics.empty()
)
