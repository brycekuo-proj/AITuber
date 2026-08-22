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
import com.aituber.poc.state.UniversalStateReducer
import com.aituber.poc.state.UniversalStateSnapshot
import kotlin.concurrent.thread

class PlaybackCaptureAiAdapter(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val targetUid: Int,
    private val targetLabel: String,
    private val reducer: UniversalStateReducer = UniversalStateReducer(),
    private val diagnostics: CaptureDiagnosticsAccumulator = CaptureDiagnosticsAccumulator()
) : AiAdapter {
    override val targetAppLabel = targetLabel
    override val detectionMethod = "Android Playback Capture"

    @Volatile private var running = false
    private var recorder: AudioRecord? = null

    override fun start(onSnapshot: (UniversalStateSnapshot) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onSnapshot(snapshot(null, CaptureStatus.RECORD_AUDIO_DENIED))
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
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUid(targetUid)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            recorder = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            running = true
            recorder?.startRecording()
            diagnostics.onNoSamples(0)
            onSnapshot(snapshot(null, CaptureStatus.CAPTURING))

            thread(name = "aituber-playback-capture", isDaemon = true) {
                val buffer = ShortArray(bufferSize / 2)
                while (running) {
                    val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val level = diagnostics.onReadResult(
                            readResult = read,
                            buffer = buffer,
                            length = read,
                            elapsedRealtimeMs = SystemClock.elapsedRealtime()
                        )
                        onSnapshot(snapshot(level, CaptureStatus.CAPTURING))
                    } else if (read == 0) {
                        diagnostics.onNoSamples(read)
                        onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
                    } else {
                        diagnostics.onReadError(read)
                        onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
                    }
                }
            }
        } catch (security: SecurityException) {
            onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
        } catch (illegal: IllegalStateException) {
            onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
        } catch (argument: IllegalArgumentException) {
            onSnapshot(snapshot(null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
        }
    }

    override fun stop() {
        running = false
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }

    private fun snapshot(level: Float?, captureStatus: String): UniversalStateSnapshot {
        val reduced = reducer.reduce(targetAppLabel, detectionMethod, level, captureStatus)
        return reduced.copy(diagnostics = diagnostics.onState(reduced.state))
    }
}
