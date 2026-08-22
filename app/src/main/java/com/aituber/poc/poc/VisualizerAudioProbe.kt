package com.aituber.poc.poc

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock

object VisualizerAudioProbe {
    private const val OUTPUT_MIX_AUDIO_SESSION = 0
    private const val TEST_DURATION_MS = 30_000L

    private val analyzer = VisualizerWaveformAnalyzer()
    private val accumulator = VisualizerProbeAccumulator()
    private var visualizer: Visualizer? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var testStartElapsedMs: Long? = null

    fun startThirtySecondTest() {
        stop()
        val now = SystemClock.elapsedRealtime()
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
                        CaptureSessionState.updateVisualizerProbe(accumulator.record(elapsed, metrics))
                        if (elapsed - now >= TEST_DURATION_MS) {
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
            accumulator.start(now, initStatus = "Initialized Visualizer(0)", captureSize = captureSize, captureRate = captureRate)
            CaptureSessionState.updateVisualizerProbe(accumulator.snapshot())
            effect.enabled = true
        } catch (exception: Throwable) {
            releaseVisualizer()
            CaptureSessionState.updateVisualizerProbe(
                accumulator.fail("${exception::class.java.simpleName}: ${exception.message ?: "Visualizer init failed"}")
            )
        }
    }

    fun stop() {
        releaseVisualizer()
        accumulator.stop()
        CaptureSessionState.updateVisualizerProbe(accumulator.snapshot())
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        testStartElapsedMs = null
    }

    private fun selectCaptureSize(range: IntArray): Int {
        val min = range.getOrNull(0) ?: 256
        val max = range.getOrNull(1) ?: min
        return 512.coerceIn(min, max)
    }

}
