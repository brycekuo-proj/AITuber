package com.aituber.poc.state

data class PlaybackProbeSnapshot(
    val callbackStatus: String,
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
            callbackStatus = "UNAVAILABLE",
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
