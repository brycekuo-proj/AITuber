package com.aituber.poc.poc

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.VisualizerWaveformMetrics

object VisualizerAudioProbe {
    private const val OUTPUT_MIX_AUDIO_SESSION = 0
    private const val TEST_DURATION_MS = 30_000L

    private val analyzer = VisualizerWaveformAnalyzer()
    private val accumulator = VisualizerProbeAccumulator()
    private val detector = VisualizerSpeakingDetector()
    private val startCoordinator = VisualizerProbeStartCoordinator(
        object : VisualizerProbeStartCoordinator.Backend {
            override fun start(automatedTest: Boolean) {
                startInternal(automatedTest)
            }

            override fun stop() {
                stopInternal()
            }
        }
    )
    private var visualizer: Visualizer? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var testStartElapsedMs: Long? = null
    private var automatedTest = false

    fun startDetector() {
        startCoordinator.startNormalCapture()
    }

    fun startThirtySecondTest() {
        startCoordinator.startThirtySecondTest()
    }

    private fun startInternal(automatedTest: Boolean) {
        releaseVisualizer()
        detector.reset()
        val now = SystemClock.elapsedRealtime()
        this.automatedTest = automatedTest
        try {
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = selectCaptureSize(captureSizeRange)
            val captureRate = (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1)
            val effect = Visualizer(OUTPUT_MIX_AUDIO_SESSION).apply {
                setCaptureSize(captureSize)
            }
            handlerThread = HandlerThread("aituber-visualizer-probe").also { thread ->
                thread.start()
                handler = Handler(thread.looper)
            }
            effect.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        val data = waveform ?: return
                        val elapsed = SystemClock.elapsedRealtime()
                        val metrics = analyzer.metrics(data)
                        val voiceSessionActive = CaptureSessionState.current().playbackProbe.voiceSessionActive == "YES"
                        val decision = detector.evaluate(
                            now = elapsed,
                            metrics = metrics,
                            voiceSessionActive = voiceSessionActive,
                            visualizerAvailable = true
                        )
                        accumulator.updateDetector(decision)
                        CaptureSessionState.updateVisualizerProbe(accumulator.record(elapsed, metrics))
                        updateUniversalState(decision.state)
                        if (this@VisualizerAudioProbe.automatedTest && elapsed - now >= TEST_DURATION_MS) {
                            stop()
                        }
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) = Unit
                },
                captureRate,
                true,
                false
            )
            visualizer = effect
            testStartElapsedMs = now
            accumulator.start(
                now = now,
                initStatus = "Initialized Visualizer(0)",
                captureSize = captureSize,
                captureRate = captureRate,
                automatedTest = automatedTest
            )
            CaptureSessionState.updateVisualizerProbe(accumulator.snapshot())
            effect.enabled = true
        } catch (exception: Throwable) {
            releaseVisualizer()
            val decision = detector.evaluate(
                now = SystemClock.elapsedRealtime(),
                metrics = VisualizerWaveformMetrics.zero(),
                voiceSessionActive = CaptureSessionState.current().playbackProbe.voiceSessionActive == "YES",
                visualizerAvailable = false
            )
            accumulator.updateDetector(decision)
            CaptureSessionState.updateVisualizerProbe(
                accumulator.fail("${exception::class.java.simpleName}: ${exception.message ?: "Visualizer init failed"}")
            )
            updateUniversalState(UniversalAiState.UNKNOWN)
        }
    }

    fun stop() {
        startCoordinator.stop()
    }

    private fun stopInternal() {
        releaseVisualizer()
        accumulator.stop()
        CaptureSessionState.updateVisualizerProbe(accumulator.snapshot())
        updateUniversalState(UniversalAiState.IDLE)
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        testStartElapsedMs = null
        automatedTest = false
    }

    private fun selectCaptureSize(range: IntArray): Int {
        val min = range.getOrNull(0) ?: 256
        val max = range.getOrNull(1) ?: min
        return 512.coerceIn(min, max)
    }

    private fun updateUniversalState(state: UniversalAiState) {
        val current = CaptureSessionState.current()
        CaptureSessionState.update(
            current.copy(
                state = state,
                audioLevel = null,
                captureStatus = "Visualizer speaking detector evaluated",
                speakingSignalSource = "Visualizer(0) waveform RMS/Peak/Activity"
            )
        )
    }

}
