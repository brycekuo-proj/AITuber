package com.aituber.poc.poc

enum class DetectionMethod(val label: String) {
    PLAYBACK_CAPTURE("Android Playback Capture"),
    SPEAKER_OUTPUT("Speaker output only (Visualizer, no recording consent)"),
    FALLBACK_MANUAL("Manual fallback")
}
