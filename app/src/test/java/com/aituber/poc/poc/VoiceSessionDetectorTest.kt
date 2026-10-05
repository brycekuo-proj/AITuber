package com.aituber.poc.poc

import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaRecorder
import com.aituber.poc.poc.VoiceSessionDetector.Player
import com.aituber.poc.poc.VoiceSessionDetector.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionDetectorTest {
    private val media = Player(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_SPEECH)
    private val music = Player(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC)
    private val notification = Player(AudioAttributes.USAGE_NOTIFICATION, AudioAttributes.CONTENT_TYPE_SONIFICATION)

    private fun eval(
        players: List<Player>,
        mode: Int = AudioManager.MODE_NORMAL,
        micOpen: Boolean = false,
        lastMic: Long? = null,
        now: Long = 100_000L
    ) = VoiceSessionDetector.evaluate(players, mode, micOpen, lastMic, now)

    @Test
    fun chatGptStyleVoiceCallIsASession() {
        val call = Player(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.CONTENT_TYPE_SPEECH)
        assertEquals(Reason.VOICE_CALL_AUDIO, eval(listOf(call)).reason)
        // WebRTC builds that leave the content type unknown count too.
        val unknownType = Player(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.CONTENT_TYPE_UNKNOWN)
        assertTrue(eval(listOf(unknownType)).active)
    }

    @Test
    fun assistantAudioIsASession() {
        val assistant = Player(VoiceSessionDetector.USAGE_ASSISTANT, AudioAttributes.CONTENT_TYPE_SPEECH)
        assertEquals(Reason.ASSISTANT_AUDIO, eval(listOf(assistant)).reason)
    }

    @Test
    fun communicationModeWithPlaybackIsASession() {
        assertEquals(Reason.COMMUNICATION_MODE, eval(listOf(media), mode = AudioManager.MODE_IN_COMMUNICATION).reason)
    }

    @Test
    fun handsFreeMediaVoiceWithOpenMicIsASession() {
        // Hands-free with barge-in: the answer plays as media while the mic stays open.
        val result = eval(listOf(media), micOpen = true)
        assertTrue(result.active)
        assertEquals(Reason.MIC_WITH_PLAYBACK, result.reason)
    }

    @Test
    fun pushToTalkAnswerAfterMicClosedStillCounts() {
        val closedAt = 100_000L
        assertTrue(eval(listOf(media), lastMic = closedAt, now = closedAt + 30_000L).active)
        assertFalse(eval(listOf(media), lastMic = closedAt, now = closedAt + VoiceSessionDetector.MIC_HOLD_MS + 1).active)
    }

    @Test
    fun musicWithoutMicIsNotASession() {
        assertFalse(eval(listOf(music)).active)
    }

    @Test
    fun noPlaybackIsNeverASession() {
        assertFalse(eval(emptyList(), mode = AudioManager.MODE_IN_COMMUNICATION, micOpen = true).active)
    }

    @Test
    fun notificationSoundWhileMicOpenIsNotASession() {
        assertFalse(eval(listOf(notification), micOpen = true).active)
    }

    @Test
    fun playbackCaptureRecorderIsNotAnExternalMic() {
        assertFalse(VoiceSessionDetector.isExternalMic(MediaRecorder.AudioSource.REMOTE_SUBMIX))
        assertTrue(VoiceSessionDetector.isExternalMic(MediaRecorder.AudioSource.VOICE_COMMUNICATION))
        assertTrue(VoiceSessionDetector.isExternalMic(MediaRecorder.AudioSource.VOICE_RECOGNITION))
        assertTrue(VoiceSessionDetector.isExternalMic(MediaRecorder.AudioSource.MIC))
        // "Hey Google" hotword recorder (MediaRecorder.AudioSource.HOTWORD, system API).
        assertFalse(VoiceSessionDetector.isExternalMic(1999))
    }

    @Test
    fun recheckIsScheduledOnlyWhileTheMicHoldRuns() {
        assertNull(VoiceSessionDetector.nextRecheckAtMs(micOpen = true, lastMicSeenMs = 1_000L, nowMs = 2_000L))
        assertNull(VoiceSessionDetector.nextRecheckAtMs(micOpen = false, lastMicSeenMs = null, nowMs = 2_000L))
        assertEquals(
            1_000L + VoiceSessionDetector.MIC_HOLD_MS,
            VoiceSessionDetector.nextRecheckAtMs(micOpen = false, lastMicSeenMs = 1_000L, nowMs = 2_000L)
        )
        assertNull(VoiceSessionDetector.nextRecheckAtMs(false, 1_000L, 1_000L + VoiceSessionDetector.MIC_HOLD_MS + 1))
    }
}
