package com.aituber.poc.state

data class PlaybackProbeEvent(
    val elapsedTimestampMs: Long,
    val activePlaybackCount: Int,
    val usage: String,
    val contentType: String
)

data class FineGrainedVoiceEvent(
    val elapsedTimestampMs: Long,
    val activePlaybackCount: Int,
    val usage: String,
    val contentType: String,
    val configurationIdentity: String,
    val publicAudioModeAndDeviceSignal: String
)

data class CombinedPlaybackRecordingEvent(
    val elapsedTimestampMs: Long,
    val playbackActiveCount: Int,
    val playbackUsage: String,
    val playbackContentType: String,
    val recordingActiveCount: Int,
    val audioSource: String,
    val clientSilenced: String,
    val recordingSessionIdentity: String,
    val audioManagerMode: String
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
    val voiceSessionActive: String,
    val probeSignalA: String,
    val probeSignalB: String,
    val probeSignalC: String,
    val actualSpeakingCandidate: String,
    val candidateConfidence: String,
    val lastCandidateChangeElapsedMs: Long?,
    val lastFineGrainedEvents: List<FineGrainedVoiceEvent>,
    val playbackSessionActive: String,
    val recordingSessionActive: String,
    val activeRecordingCount: Int,
    val recordingCallbackEventCount: Int,
    val observedAudioSource: String,
    val clientSilenced: String,
    val recordingSessionIdentity: String,
    val combinedCandidateState: String,
    val combinedCandidateConfidence: String,
    val lastCombinedStateChangeElapsedMs: Long?,
    val lastCombinedEvents: List<CombinedPlaybackRecordingEvent>,
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
            voiceSessionActive = "NO",
            probeSignalA = "Configuration identity: n/a",
            probeSignalB = "Public audio mode/device: n/a",
            probeSignalC = "Callback timing: n/a",
            actualSpeakingCandidate = "NO",
            candidateConfidence = "HIGH",
            lastCandidateChangeElapsedMs = null,
            lastFineGrainedEvents = emptyList(),
            playbackSessionActive = "NO",
            recordingSessionActive = "NO",
            activeRecordingCount = 0,
            recordingCallbackEventCount = 0,
            observedAudioSource = "n/a",
            clientSilenced = "n/a",
            recordingSessionIdentity = "n/a",
            combinedCandidateState = "UNKNOWN",
            combinedCandidateConfidence = "NONE",
            lastCombinedStateChangeElapsedMs = null,
            lastCombinedEvents = emptyList(),
            attribution = "UNSUPPORTED - Attribution unavailable"
        )
    }
}
