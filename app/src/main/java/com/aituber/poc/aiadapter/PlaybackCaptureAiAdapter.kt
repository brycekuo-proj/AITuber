package com.aituber.poc.aiadapter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.annotation.RequiresApi
import com.aituber.poc.state.UniversalStateReducer
import com.aituber.poc.state.UniversalStateSnapshot
import kotlin.concurrent.thread
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.Q)
class PlaybackCaptureAiAdapter(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val reducer: UniversalStateReducer = UniversalStateReducer()
) : AiAdapter {
    override val targetAppLabel = "System playback"
    override val detectionMethod = "Android Playback Capture"

    @Volatile private var running = false
    private var recorder: AudioRecord? = null

    override fun start(onSnapshot: (UniversalStateSnapshot) -> Unit) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, null, CaptureStatus.RECORD_AUDIO_DENIED))
            return
        }

        try {
            val sampleRate = 44100
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = minBuffer.coerceAtLeast(sampleRate / 10)
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
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
            onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, 0f, CaptureStatus.CAPTURING))

            thread(name = "aituber-playback-capture", isDaemon = true) {
                val buffer = ShortArray(bufferSize / 2)
                while (running) {
                    val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val level = rms(buffer, read)
                        onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, level, CaptureStatus.CAPTURING))
                    } else {
                        onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
                    }
                }
            }
        } catch (security: SecurityException) {
            onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
        } catch (illegal: IllegalStateException) {
            onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
        } catch (argument: IllegalArgumentException) {
            onSnapshot(reducer.reduce(targetAppLabel, detectionMethod, null, CaptureStatus.BLOCKED_BY_SOURCE_APP))
        }
    }

    override fun stop() {
        running = false
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
    }

    private fun rms(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (index in 0 until length) {
            val normalized = buffer[index] / Short.MAX_VALUE.toDouble()
            sum += normalized * normalized
        }
        return sqrt(sum / length).toFloat()
    }
}
