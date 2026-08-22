package com.aituber.poc.poc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.aituber.poc.state.PlaybackProbeSnapshot

class AndroidPlaybackStateProbe(
    context: Context,
    private val onSnapshot: (PlaybackProbeSnapshot) -> Unit
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var callback: AudioManager.AudioPlaybackCallback? = null
    private var callbackEventCount = 0
    private var lastCallbackElapsedMs: Long? = null

    fun start() {
        if (callback != null) return
        callbackEventCount = 0
        lastCallbackElapsedMs = null
        onSnapshot(
            PlaybackProbeSnapshot.empty().copy(
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
                PlaybackProbeSnapshot.empty().copy(
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
        onSnapshot(
            PlaybackProbeSnapshot(
                callbackStatus = "AVAILABLE",
                registrationAttempted = "YES",
                registrationResult = registrationResult,
                callbackEventCount = callbackEventCount,
                lastCallbackElapsedMs = lastCallbackElapsedMs,
                activePlaybackCount = configs.size,
                chatGptPlaybackDetected = "UNKNOWN",
                chatGptPlaybackState = "UNKNOWN",
                lastPlaybackChangeElapsedMs = SystemClock.elapsedRealtime(),
                observedUsage = attributes.map { usageLabel(it.usage) }.distinct().joinToStringOrDefault(),
                observedContentType = attributes.map { contentTypeLabel(it.contentType) }.distinct().joinToStringOrDefault(),
                observedPlayerState = "UNAVAILABLE",
                attribution = "UNSUPPORTED - Attribution unavailable"
            )
        )
    }

    private fun List<String>.joinToStringOrDefault(): String {
        return if (isEmpty()) "n/a" else joinToString(", ")
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
