package com.aituber.poc.state

data class PlaybackProbeSnapshot(
    val callbackStatus: String,
    val registrationAttempted: String,
    val registrationResult: String,
    val callbackEventCount: Int,
    val lastCallbackElapsedMs: Long?,
    val activePlaybackCount: Int,
    val chatGptPlaybackDetected: String,
    val chatGptPlaybackState: String,
    val lastPlaybackChangeElapsedMs: Long?,
    val observedUsage: String,
    val observedContentType: String,
    val observedPlayerState: String,
    val attribution: String
) {
    companion object {
        fun empty() = PlaybackProbeSnapshot(
            callbackStatus = "NOT_REGISTERED",
            registrationAttempted = "NO",
            registrationResult = "Not attempted",
            callbackEventCount = 0,
            lastCallbackElapsedMs = null,
            activePlaybackCount = 0,
            chatGptPlaybackDetected = "UNKNOWN",
            chatGptPlaybackState = "UNKNOWN",
            lastPlaybackChangeElapsedMs = null,
            observedUsage = "n/a",
            observedContentType = "n/a",
            observedPlayerState = "UNAVAILABLE",
            attribution = "UNSUPPORTED - Attribution unavailable"
        )
    }
}
