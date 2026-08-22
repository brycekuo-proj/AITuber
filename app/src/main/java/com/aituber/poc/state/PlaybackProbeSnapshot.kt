package com.aituber.poc.state

data class PlaybackProbeEvent(
    val elapsedTimestampMs: Long,
    val activePlaybackCount: Int,
    val usage: String,
    val contentType: String
)

data class PlaybackProbeSnapshot(
    val callbackStatus: String,
    val registrationAttempted: String,
    val registrationResult: String,
    val callbackEventCount: Int,
    val lastCallbackElapsedMs: Long?,
    val activePlaybackCount: Int,
    val peakActivePlaybackCount: Int,
    val activePlaybackEvents: Int,
    val playbackBecameActiveCount: Int,
    val playbackBecameInactiveCount: Int,
    val lastNonZeroActiveCount: Int,
    val lastActiveElapsedMs: Long?,
    val lastObservedUsageWhileActive: String,
    val lastObservedContentTypeWhileActive: String,
    val lastPlaybackEvents: List<PlaybackProbeEvent>,
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
            peakActivePlaybackCount = 0,
            activePlaybackEvents = 0,
            playbackBecameActiveCount = 0,
            playbackBecameInactiveCount = 0,
            lastNonZeroActiveCount = 0,
            lastActiveElapsedMs = null,
            lastObservedUsageWhileActive = "n/a",
            lastObservedContentTypeWhileActive = "n/a",
            lastPlaybackEvents = emptyList(),
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
