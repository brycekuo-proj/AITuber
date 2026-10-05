package com.aituber.poc.claudechat

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.aituber.poc.poc.VisualizerWaveformAnalyzer
import com.aituber.poc.state.VisualizerWaveformMetrics
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reads Claude's sentences aloud with the phone's text-to-speech and measures
 * how loud each moment is, so the character's mouth follows the voice.
 *
 * The speech plays in an audio session of our own; a Visualizer on that
 * session gives the loudness (it needs RECORD_AUDIO, which AITuber already
 * asks for). Without it [loudnessAvailable] is false and the mouth falls back
 * to a generic talking motion.
 */
class ClaudeSpeaker(
    context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val analyzer = VisualizerWaveformAnalyzer()
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val sessionId: Int = audioManager?.generateAudioSessionId() ?: AudioManager.ERROR
    private val pending = AtomicInteger(0)
    private val utteranceIds = AtomicInteger(0)
    /** Bumped by stop(): callbacks of utterances from before it are ignored. */
    @Volatile private var generation = 0
    private val waiting = mutableListOf<String>()
    private var ready = false
    /** The phone has no working text-to-speech; the chat falls back to silent talking. */
    var failed = false
        private set
    @Volatile private var meterHeardSound = false
    private var meterEnabled = false
    private var released = false
    private var visualizer: Visualizer? = null
    private var currentLanguage: Locale? = null

    @Volatile
    var latestMetrics: VisualizerWaveformMetrics = VisualizerWaveformMetrics.zero()
        private set

    /**
     * True once the meter has actually heard our voice: a TTS engine that ignores our
     * audio session leaves it silent, and then the mouth uses a generic motion instead.
     */
    val loudnessAvailable: Boolean
        get() = meterEnabled && meterHeardSound

    /** Sentences queued or playing. */
    val hasWork: Boolean
        get() = pending.get() > 0 || waiting.isNotEmpty()

    var speaking: Boolean = false
        private set

    private val tts: TextToSpeech = TextToSpeech(appContext) { status ->
        main.post { onTtsReady(status == TextToSpeech.SUCCESS) }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (isCurrent(utteranceId)) main.post { if (isCurrent(utteranceId)) setSpeaking(true) }
            }

            override fun onDone(utteranceId: String?) = finished(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finished(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
        })
    }

    /** Queues one sentence. */
    fun speak(sentence: String) {
        if (released || failed || sentence.isBlank()) return
        if (!ready) {
            waiting += sentence
            return
        }
        val language = languageFor(sentence)
        if (language != currentLanguage) {
            runCatching { tts.language = language }
            currentLanguage = language
        }
        val params = Bundle().apply {
            if (sessionId != AudioManager.ERROR) putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, sessionId)
        }
        pending.incrementAndGet()
        val id = "aituber-$generation-${utteranceIds.incrementAndGet()}"
        val result = tts.speak(sentence, TextToSpeech.QUEUE_ADD, params, id)
        if (result != TextToSpeech.SUCCESS) finished(id)
    }

    /** Stops talking now and forgets queued sentences. */
    fun stop() {
        generation += 1
        waiting.clear()
        pending.set(0)
        runCatching { tts.stop() }
        setSpeaking(false)
    }

    fun release() {
        if (released) return
        released = true
        stop()
        releaseVisualizer()
        runCatching { tts.shutdown() }
    }

    private fun onTtsReady(ok: Boolean) {
        if (released) return
        ready = ok
        if (!ok) {
            Log.w(TAG, "text-to-speech unavailable")
            failed = true
            waiting.clear()
            return
        }
        val queued = waiting.toList()
        waiting.clear()
        queued.forEach(::speak)
    }

    private fun isCurrent(utteranceId: String?): Boolean =
        utteranceId?.startsWith("aituber-$generation-") == true

    private fun finished(utteranceId: String?) {
        if (!isCurrent(utteranceId)) return
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            main.post { if (pending.get() == 0) setSpeaking(false) }
        }
    }

    private fun setSpeaking(value: Boolean) {
        if (released && value) return
        if (value) {
            attachVisualizer()
        } else {
            setMeterEnabled(false)
            latestMetrics = VisualizerWaveformMetrics.zero()
        }
        if (speaking == value) return
        speaking = value
        onSpeakingChanged(value)
    }

    private fun attachVisualizer() {
        if (visualizer != null) {
            setMeterEnabled(true)
            return
        }
        if (sessionId == AudioManager.ERROR) return
        try {
            val effect = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            effect.captureSize = 512.coerceIn(range[0], range[1])
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform == null) return
                        val metrics = analyzer.metrics(waveform)
                        if (metrics.peak > HEARD_PEAK) meterHeardSound = true
                        latestMetrics = metrics
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) = Unit
                },
                Visualizer.getMaxCaptureRate(),
                true,
                false
            )
            visualizer = effect
            setMeterEnabled(true)
        } catch (e: Throwable) {
            // No RECORD_AUDIO, or the device refuses a Visualizer on this session.
            Log.w(TAG, "visualizer unavailable: ${e.javaClass.simpleName}")
            runCatching { visualizer?.release() }
            visualizer = null
        }
    }

    private fun setMeterEnabled(enabled: Boolean) {
        val effect = visualizer ?: return
        if (meterEnabled == enabled) return
        val status = runCatching { effect.setEnabled(enabled) }.getOrDefault(Visualizer.ERROR)
        meterEnabled = enabled && status == Visualizer.SUCCESS
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        meterEnabled = false
    }

    companion object {
        private const val TAG = "ClaudeSpeaker"
        private const val HEARD_PEAK = 0.02
        private val TAIWAN_CHINESE: Locale = Locale.TRADITIONAL_CHINESE
        private val JAPANESE: Locale = Locale.JAPANESE

        /** Chinese text gets a Chinese voice, kana a Japanese one, the rest the phone's language. */
        fun languageFor(text: String): Locale {
            val hasKana = text.any { it in '぀'..'ヿ' }
            if (hasKana) return JAPANESE
            val hasHan = text.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }
            if (hasHan) return TAIWAN_CHINESE
            val device = Locale.getDefault()
            return if (device.language == "zh" || device.language == "ja") Locale.US else device
        }
    }
}
