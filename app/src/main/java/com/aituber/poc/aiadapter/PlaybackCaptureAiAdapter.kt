package com.aituber.poc.aiadapter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.SystemClock
import com.aituber.poc.state.CaptureDiagnosticsAccumulator
import com.aituber.poc.state.MouthSignal
import com.aituber.poc.state.UniversalStateReducer
import com.aituber.poc.state.UniversalStateSnapshot
import com.aituber.poc.state.VisualizerWaveformMetrics
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Android Playback Capture of the target AI apps' MEDIA / GAME / UNKNOWN audio
 * (e.g. "read aloud", or a voice mode that plays as media). Voice-call style audio (USAGE_VOICE_COMMUNICATION, used by
 * live voice modes) cannot be captured by third-party apps; the output-mix
 * Visualizer covers that case.
 */
class PlaybackCaptureAiAdapter(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val targetUids: List<Int>,
    private val targetLabel: String,
    private val reducer: UniversalStateReducer = UniversalStateReducer(),
    private val diagnostics: CaptureDiagnosticsAccumulator = CaptureDiagnosticsAccumulator()
) : AiAdapter {
    override val targetAppLabel = targetLabel
    override val detectionMethod = "Android Playback Capture"

    @Volatile private var running = false
    /** Set by stop() when the reader did not exit in time; the reader then releases the recorder. */
    @Volatile private var releaseOnReaderExit = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    override fun start(onSnapshot: (UniversalStateSnapshot) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onSnapshot(snapshot(null, CaptureStatus.RECORD_AUDIO_DENIED, null))
            return
        }
        if (targetUids.isEmpty()) {
            onSnapshot(snapshot(null, CaptureStatus.NO_KNOWN_AI_APP, null))
            return
        }

        try {
            diagnostics.reset()
            val sampleRate = 44100
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBuffer.coerceAtLeast(sampleRate / 10)
            val builder = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                // Some voice players leave the usage unset; UNKNOWN is the third capturable usage.
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            targetUids.distinct().forEach { builder.addMatchingUid(it) }

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(builder.build())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
            recorder = record

            running = true
            record.startRecording()
            diagnostics.onNoSamples(0)
            onSnapshot(snapshot(null, CaptureStatus.CAPTURING, null))

            worker = Thread({ readLoop(record, bufferSize, onSnapshot) }, "aituber-playback-capture").apply {
                isDaemon = true
                start()
            }
        } catch (security: SecurityException) {
            onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP, null))
        } catch (illegal: IllegalStateException) {
            onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP, null))
        } catch (argument: IllegalArgumentException) {
            onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP, null))
        }
    }

    private fun readLoop(record: AudioRecord, bufferSize: Int, onSnapshot: (UniversalStateSnapshot) -> Unit) {
        val buffer = ShortArray(bufferSize / 2)
        var lastNonSilentMs = SystemClock.elapsedRealtime()
        var consecutiveErrors = 0
        try {
            while (running) {
                val read = record.read(buffer, 0, buffer.size)
                when {
                    read > 0 -> {
                        consecutiveErrors = 0
                        val now = SystemClock.elapsedRealtime()
                        val level = diagnostics.onReadResult(
                            readResult = read,
                            buffer = buffer,
                            length = read,
                            elapsedRealtimeMs = now
                        )
                        val metrics = PcmMetrics.normalized(buffer, read)
                        if (metrics.peak > 0.0) lastNonSilentMs = now
                        // An app that opts out of capture yields zero-filled buffers, which
                        // look exactly like silence. Say so instead of claiming "Capturing".
                        val status = if (now - lastNonSilentMs > SILENT_REPORT_MS) {
                            CaptureStatus.CAPTURING_SILENT
                        } else {
                            CaptureStatus.CAPTURING
                        }
                        onSnapshot(snapshot(level, status, metrics))
                    }
                    read == 0 -> {
                        diagnostics.onNoSamples(read)
                        onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP, null))
                        Thread.sleep(ERROR_BACKOFF_MS)
                    }
                    else -> {
                        // ERROR_INVALID_OPERATION / ERROR_DEAD_OBJECT etc.: do not spin at 100% CPU.
                        diagnostics.onReadError(read)
                        onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP, null))
                        consecutiveErrors += 1
                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            // e.g. audioserver restarted (ERROR_DEAD_OBJECT): say so plainly.
                            onSnapshot(snapshot(null, CaptureStatus.CAPTURE_FAILED, null))
                            break
                        }
                        Thread.sleep(ERROR_BACKOFF_MS)
                    }
                }
            }
        } catch (_: InterruptedException) {
            // stop() interrupted the backoff sleep.
        } catch (e: RuntimeException) {
            onSnapshot(snapshot(null, CaptureStatus.CAPTURE_FAILED, null))
        } finally {
            if (releaseOnReaderExit) runCatching { record.release() }
        }
    }

    override fun stop() {
        running = false
        val thread = worker
        worker = null
        thread?.interrupt()
        // Let the reader leave read() before the recorder is released under it.
        runCatching { recorder?.stop() }
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(STOP_JOIN_MS) }
        }
        if (thread != null && thread.isAlive) {
            // Still inside read(): releasing now could free the native recorder under it.
            releaseOnReaderExit = true
        } else {
            recorder?.release()
        }
        recorder = null
    }

    private fun snapshot(level: Float?, captureStatus: String, metrics: VisualizerWaveformMetrics?): UniversalStateSnapshot {
        val reduced = reducer.reduce(targetAppLabel, detectionMethod, level, captureStatus)
        return reduced.copy(
            diagnostics = diagnostics.onState(reduced.state),
            playbackSignal = if (metrics != null) {
                MouthSignal(MouthSignal.Source.PLAYBACK_CAPTURE, available = true, metrics = metrics)
            } else {
                MouthSignal.NONE
            }
        )
    }

    companion object {
        private const val SILENT_REPORT_MS = 3_000L
        private const val ERROR_BACKOFF_MS = 200L
        private const val MAX_CONSECUTIVE_ERRORS = 25
        private const val STOP_JOIN_MS = 500L
    }
}

/** Loudness of 16-bit PCM on the same 0..1 scale as the Visualizer metrics. */
internal object PcmMetrics {
    /** Samples quieter than this (about 3/128 of full scale, like the Visualizer epsilon) count as inactive. */
    private const val ACTIVITY_FLOOR = 768

    /** Below this peak the buffer counts as silence and is not amplified. */
    private const val NOISE_PEAK = 0.01

    /**
     * Like the Visualizer's default SCALING_MODE_NORMALIZED: loudness relative to
     * the buffer's own peak, so quiet TTS and loud TTS open the mouth alike and the
     * existing mouth mapper thresholds (tuned on the Visualizer) still fit.
     */
    fun normalized(buffer: ShortArray, length: Int): VisualizerWaveformMetrics {
        val raw = of(buffer, length)
        if (raw.peak < NOISE_PEAK) return raw
        val peakAbs = raw.peak * 32768.0
        var active = 0
        for (i in 0 until length) {
            if (abs(buffer[i].toInt()) > peakAbs * 3.0 / 128.0) active += 1
        }
        return VisualizerWaveformMetrics(
            rms = (raw.rms / raw.peak).coerceIn(0.0, 1.0),
            peak = 1.0,
            activityRatio = (active.toDouble() / length).coerceIn(0.0, 1.0)
        )
    }

    fun of(buffer: ShortArray, length: Int): VisualizerWaveformMetrics {
        if (length <= 0) return VisualizerWaveformMetrics.zero()
        var squareSum = 0.0
        var peak = 0
        var active = 0
        for (i in 0 until length) {
            val v = buffer[i].toInt()
            val a = abs(v)
            squareSum += v.toDouble() * v
            if (a > peak) peak = a
            if (a > ACTIVITY_FLOOR) active += 1
        }
        return VisualizerWaveformMetrics(
            rms = (sqrt(squareSum / length) / 32768.0).coerceIn(0.0, 1.0),
            peak = (peak / 32768.0).coerceIn(0.0, 1.0),
            activityRatio = (active.toDouble() / length).coerceIn(0.0, 1.0)
        )
    }
}
