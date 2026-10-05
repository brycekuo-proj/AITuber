package com.aituber.poc.poc

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder

/**
 * Decides whether a spoken AI conversation is going on, from what any app may
 * see: the usage of active players, the audio mode and whether another app has
 * the microphone open. Players and recorders are anonymous to us, so this never
 * names the app.
 *
 * AI voice modes do not all look alike:
 *  - ChatGPT plays its voice as a call (USAGE_VOICE_COMMUNICATION);
 *  - other apps may play media or assistant audio while keeping the microphone
 *    open so the user can talk over them (Claude's hands-free voice mode allows
 *    barge-in, so its microphone stays open);
 *  - in push-to-talk mode the microphone is only open while the button is held,
 *    so a recently closed microphone still counts for [MIC_HOLD_MS].
 *
 * The microphone rule is what keeps music out: a music player never holds the mic.
 */
object VoiceSessionDetector {
    /** How long after the microphone closes an answer still counts as part of the conversation. */
    const val MIC_HOLD_MS = 60_000L

    /** AudioAttributes.USAGE_ASSISTANT (API 26); spelled out so the probe compiles against any SDK stub. */
    const val USAGE_ASSISTANT = 16

    data class Player(val usage: Int, val contentType: Int)

    enum class Reason(val label: String) {
        NONE("none"),
        VOICE_CALL_AUDIO("voice-call audio"),
        ASSISTANT_AUDIO("assistant audio"),
        COMMUNICATION_MODE("communication mode"),
        MIC_WITH_PLAYBACK("mic + playback")
    }

    data class Result(val active: Boolean, val reason: Reason) {
        companion object {
            val INACTIVE = Result(false, Reason.NONE)
        }
    }

    /**
     * A microphone opened by a voice app. Excluded: REMOTE_SUBMIX (AITuber's own playback
     * capture) and system sources such as the always-on "Hey Google" HOTWORD recorder,
     * which would otherwise make any music look like a conversation.
     */
    fun isExternalMic(audioSource: Int): Boolean = audioSource in VOICE_APP_MIC_SOURCES

    private val VOICE_APP_MIC_SOURCES = setOf(
        MediaRecorder.AudioSource.DEFAULT,
        MediaRecorder.AudioSource.MIC,
        MediaRecorder.AudioSource.CAMCORDER,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.VOICE_PERFORMANCE
    )

    fun evaluate(
        players: List<Player>,
        audioMode: Int,
        micOpen: Boolean,
        lastMicSeenMs: Long?,
        nowMs: Long
    ): Result {
        if (players.isEmpty()) return Result.INACTIVE
        val reason = when {
            players.any { it.usage == AudioAttributes.USAGE_VOICE_COMMUNICATION } -> Reason.VOICE_CALL_AUDIO
            players.any { it.usage == USAGE_ASSISTANT } -> Reason.ASSISTANT_AUDIO
            audioMode == AudioManager.MODE_IN_COMMUNICATION -> Reason.COMMUNICATION_MODE
            players.any { it.usage in SPEECH_CAPABLE_USAGES } && micRecent(micOpen, lastMicSeenMs, nowMs) ->
                Reason.MIC_WITH_PLAYBACK
            else -> Reason.NONE
        }
        return Result(reason != Reason.NONE, reason)
    }

    /** When the decision may change without any new audio callback (the mic hold running out). */
    fun nextRecheckAtMs(micOpen: Boolean, lastMicSeenMs: Long?, nowMs: Long): Long? {
        if (micOpen || lastMicSeenMs == null) return null
        val expiry = lastMicSeenMs + MIC_HOLD_MS
        return if (expiry > nowMs) expiry else null
    }

    private fun micRecent(micOpen: Boolean, lastMicSeenMs: Long?, nowMs: Long): Boolean =
        micOpen || (lastMicSeenMs != null && nowMs - lastMicSeenMs <= MIC_HOLD_MS)

    /** Notification and UI sounds do not make a conversation. */
    private val SPEECH_CAPABLE_USAGES = setOf(
        AudioAttributes.USAGE_MEDIA,
        AudioAttributes.USAGE_UNKNOWN,
        AudioAttributes.USAGE_GAME,
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
    )
}
