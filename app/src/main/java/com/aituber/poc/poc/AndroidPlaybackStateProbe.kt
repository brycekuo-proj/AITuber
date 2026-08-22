package com.aituber.poc.poc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.aituber.poc.aiadapter.VoiceCommunicationPlaybackAdapter
import com.aituber.poc.state.FineGrainedVoiceEvent
import com.aituber.poc.state.PlaybackProbeEvent
import com.aituber.poc.state.PlaybackProbeSnapshot
import com.aituber.poc.state.UniversalStateSnapshot
import kotlin.math.max

class AndroidPlaybackStateProbe(
    context: Context,
    private val currentUniversalState: () -> UniversalStateSnapshot,
    private val onSnapshot: (PlaybackProbeSnapshot) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val speakingAdapter = VoiceCommunicationPlaybackAdapter()
    private var callback: AudioManager.AudioPlaybackCallback? = null
    private var callbackEventCount = 0
    private var lastCallbackElapsedMs: Long? = null
    private var lastActivePlaybackCount = 0
    private var peakActivePlaybackCount = 0
    private var activePlaybackEvents = 0
    private var playbackBecameActiveCount = 0
    private var playbackBecameInactiveCount = 0
    private var lastNonZeroActiveCount = 0
    private var lastActiveElapsedMs: Long? = null
    private var lastObservedUsageWhileActive = "n/a"
    private var lastObservedContentTypeWhileActive = "n/a"
    private val lastPlaybackEvents = ArrayDeque<PlaybackProbeEvent>(10)
    private val lastFineGrainedEvents = ArrayDeque<FineGrainedVoiceEvent>(20)
    private var lastConfigurationIdentity = "n/a"
    private var lastCandidate = "NO"
    private var lastCandidateChangeElapsedMs: Long? = null
    private var previousCallbackElapsedMs: Long? = null

    fun start() {
        if (callback != null) return
        onSnapshot(
            currentSnapshot().copy(
                callbackStatus = "REGISTERING",
                registrationAttempted = "YES",
                registrationResult = "Attempting registration"
            )
        )

        val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                callbackEventCount += 1
                lastCallbackElapsedMs = SystemClock.elapsedRealtime()
                publish(configs.orEmpty())
            }
        }
        callback = playbackCallback

        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, Handler(Looper.getMainLooper()))
            publish(audioManager.activePlaybackConfigurations, "Registered; no callback seen yet")
        } catch (runtime: RuntimeException) {
            callback = null
            onSnapshot(
                currentSnapshot().copy(
                    callbackStatus = "UNAVAILABLE",
                    registrationAttempted = "YES",
                    registrationResult = "Registration error: ${runtime.javaClass.simpleName}",
                    attribution = "UNSUPPORTED - Attribution unavailable"
                )
            )
        }
    }

    fun stop() {
        callback?.let { playbackCallback ->
            runCatching { audioManager.unregisterAudioPlaybackCallback(playbackCallback) }
        }
        callback = null
    }

    private fun publish(
        configs: List<AudioPlaybackConfiguration>,
        registrationResult: String = "Registered; callback received"
    ) {
        val attributes = configs.map { config -> config.audioAttributes }
        val now = SystemClock.elapsedRealtime()
        val usage = attributes.map { usageLabel(it.usage) }.distinct().joinToStringOrDefault()
        val contentType = attributes.map { contentTypeLabel(it.contentType) }.distinct().joinToStringOrDefault()
        val activeCount = configs.size
        val configurationIdentity = configs.map { config -> config.hashCode().toString() }
            .sorted()
            .joinToStringOrDefault()
        val configurationIdentityChanged = configurationIdentity != lastConfigurationIdentity
        lastConfigurationIdentity = configurationIdentity
        val publicAudioModeAndDeviceSignal = publicAudioModeAndDeviceSignal(configs)
        val callbackDeltaMs = previousCallbackElapsedMs?.let { previous -> now - previous }
        previousCallbackElapsedMs = now
        val hasVoiceCommunicationSpeech = attributes.any { attribute ->
            attribute.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION &&
                attribute.contentType == AudioAttributes.CONTENT_TYPE_SPEECH
        }
        val voiceSessionActive = activeCount > 0 && hasVoiceCommunicationSpeech
        val actualCandidate = if (voiceSessionActive) "UNKNOWN" else "NO"
        val candidateConfidence = if (voiceSessionActive) "NONE" else "HIGH"
        if (actualCandidate != lastCandidate) {
            lastCandidate = actualCandidate
            lastCandidateChangeElapsedMs = now
        }

        recordPlaybackEvent(
            PlaybackProbeEvent(
                elapsedTimestampMs = now,
                activePlaybackCount = activeCount,
                usage = usage,
                contentType = contentType
            )
        )
        recordFineGrainedEvent(
            FineGrainedVoiceEvent(
                elapsedTimestampMs = now,
                activePlaybackCount = activeCount,
                usage = usage,
                contentType = contentType,
                configurationIdentity = configurationIdentity,
                publicAudioModeAndDeviceSignal = publicAudioModeAndDeviceSignal
            )
        )

        if (activeCount > 0) {
            activePlaybackEvents += 1
            peakActivePlaybackCount = max(peakActivePlaybackCount, activeCount)
            lastNonZeroActiveCount = activeCount
            lastActiveElapsedMs = now
            lastObservedUsageWhileActive = usage
            lastObservedContentTypeWhileActive = contentType
        }
        if (lastActivePlaybackCount == 0 && activeCount > 0) {
            playbackBecameActiveCount += 1
        }
        if (lastActivePlaybackCount > 0 && activeCount == 0) {
            playbackBecameInactiveCount += 1
        }
        lastActivePlaybackCount = activeCount

        onSnapshot(
            currentSnapshot().copy(
                callbackStatus = "AVAILABLE",
                registrationAttempted = "YES",
                registrationResult = registrationResult,
                chatGptPlaybackDetected = "UNKNOWN",
                chatGptPlaybackState = "UNKNOWN",
                lastPlaybackChangeElapsedMs = now,
                observedUsage = usage,
                observedContentType = contentType,
                observedPlayerState = "UNAVAILABLE",
                voiceSessionActive = if (voiceSessionActive) "YES" else "NO",
                probeSignalA = "Configuration identity changed=$configurationIdentityChanged value=$configurationIdentity",
                probeSignalB = publicAudioModeAndDeviceSignal,
                probeSignalC = "Callback delta=${callbackDeltaMs?.let { "$it ms" } ?: "n/a"} events=$callbackEventCount",
                actualSpeakingCandidate = actualCandidate,
                candidateConfidence = candidateConfidence,
                lastCandidateChangeElapsedMs = lastCandidateChangeElapsedMs,
                attribution = "UNSUPPORTED - Attribution unavailable"
            )
        )

        val speakingResult = speakingAdapter.evaluate(activeCount, hasVoiceCommunicationSpeech)
        CaptureSessionState.update(
            currentUniversalState().copy(
                state = speakingResult.state,
                audioLevel = null,
                captureStatus = "Playback callback heuristic evaluated",
                speakingSignalSource = speakingResult.signalSource
            )
        )
    }

    private fun recordPlaybackEvent(event: PlaybackProbeEvent) {
        if (lastPlaybackEvents.size == 10) {
            lastPlaybackEvents.removeFirst()
        }
        lastPlaybackEvents.addLast(event)
    }

    private fun recordFineGrainedEvent(event: FineGrainedVoiceEvent) {
        if (lastFineGrainedEvents.size == 20) {
            lastFineGrainedEvents.removeFirst()
        }
        lastFineGrainedEvents.addLast(event)
    }

    private fun currentSnapshot() = PlaybackProbeSnapshot.empty().copy(
        callbackEventCount = callbackEventCount,
        lastCallbackElapsedMs = lastCallbackElapsedMs,
        activePlaybackCount = lastActivePlaybackCount,
        peakActivePlaybackCount = peakActivePlaybackCount,
        activePlaybackEvents = activePlaybackEvents,
        playbackBecameActiveCount = playbackBecameActiveCount,
        playbackBecameInactiveCount = playbackBecameInactiveCount,
        lastNonZeroActiveCount = lastNonZeroActiveCount,
        lastActiveElapsedMs = lastActiveElapsedMs,
        lastObservedUsageWhileActive = lastObservedUsageWhileActive,
        lastObservedContentTypeWhileActive = lastObservedContentTypeWhileActive,
        lastPlaybackEvents = lastPlaybackEvents.toList(),
        lastCandidateChangeElapsedMs = lastCandidateChangeElapsedMs,
        lastFineGrainedEvents = lastFineGrainedEvents.toList()
    )

    private fun List<String>.joinToStringOrDefault(): String {
        return if (isEmpty()) "n/a" else joinToString(", ")
    }

    private fun publicAudioModeAndDeviceSignal(configs: List<AudioPlaybackConfiguration>): String {
        val mode = modeLabel(audioManager.mode)
        val communicationDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { device ->
                "communicationDevice=type:${device.type}/id:${device.id}"
            } ?: "communicationDevice=null"
        } else {
            "communicationDevice=UNSUPPORTED_API_${Build.VERSION.SDK_INT}"
        }
        val playbackDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            configs.mapNotNull { config -> config.audioDeviceInfo }
                .map { device -> "playbackDevice=type:${device.type}/id:${device.id}" }
                .distinct()
                .joinToStringOrDefault()
        } else {
            "playbackDevice=UNSUPPORTED_API_${Build.VERSION.SDK_INT}"
        }
        return "mode=$mode $communicationDevice $playbackDevices"
    }

    private fun modeLabel(mode: Int): String {
        return when (mode) {
            AudioManager.MODE_NORMAL -> "MODE_NORMAL"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
            else -> "MODE_$mode"
        }
    }

    private fun usageLabel(usage: Int): String {
        return when (usage) {
            AudioAttributes.USAGE_MEDIA -> "USAGE_MEDIA"
            AudioAttributes.USAGE_VOICE_COMMUNICATION -> "USAGE_VOICE_COMMUNICATION"
            AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING -> "USAGE_VOICE_COMMUNICATION_SIGNALLING"
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY -> "USAGE_ASSISTANCE_ACCESSIBILITY"
            AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "USAGE_ASSISTANCE_NAVIGATION_GUIDANCE"
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "USAGE_ASSISTANCE_SONIFICATION"
            AudioAttributes.USAGE_GAME -> "USAGE_GAME"
            AudioAttributes.USAGE_ALARM -> "USAGE_ALARM"
            AudioAttributes.USAGE_NOTIFICATION -> "USAGE_NOTIFICATION"
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "USAGE_NOTIFICATION_RINGTONE"
            AudioAttributes.USAGE_UNKNOWN -> "USAGE_UNKNOWN"
            else -> "USAGE_$usage"
        }
    }

    private fun contentTypeLabel(contentType: Int): String {
        return when (contentType) {
            AudioAttributes.CONTENT_TYPE_SPEECH -> "CONTENT_TYPE_SPEECH"
            AudioAttributes.CONTENT_TYPE_MUSIC -> "CONTENT_TYPE_MUSIC"
            AudioAttributes.CONTENT_TYPE_MOVIE -> "CONTENT_TYPE_MOVIE"
            AudioAttributes.CONTENT_TYPE_SONIFICATION -> "CONTENT_TYPE_SONIFICATION"
            AudioAttributes.CONTENT_TYPE_UNKNOWN -> "CONTENT_TYPE_UNKNOWN"
            else -> "CONTENT_TYPE_$contentType"
        }
    }
}
