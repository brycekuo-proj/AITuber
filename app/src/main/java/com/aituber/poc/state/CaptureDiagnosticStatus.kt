package com.aituber.poc.state

enum class CaptureDiagnosticStatus(val label: String) {
    NO_SAMPLES("No samples"),
    RECEIVING_SILENCE("Receiving silence"),
    RECEIVING_AUDIO("Receiving audio"),
    AUDIO_RECORD_ERROR("AudioRecord error")
}
