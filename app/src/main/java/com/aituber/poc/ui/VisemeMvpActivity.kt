package com.aituber.poc.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.viseme.MandarinVisemeClassifier
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class VisemeMvpActivity : Activity() {
    private val sampleRate = 16_000
    private val frameSamples = 512 // 32 ms @ 16 kHz
    private val permissionRequestCode = 2201
    private val running = AtomicBoolean(false)
    private val classifier = MandarinVisemeClassifier(sampleRate = sampleRate)

    private lateinit var visemeValue: TextView
    private lateinit var imageView: ImageView
    private lateinit var metricsValue: TextView
    private lateinit var statusValue: TextView
    private lateinit var toggleButton: Button

    @Volatile
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var lastUiViseme = MandarinVisemeClassifier.Viseme.REST
    private var startedAtMs = 0L
    private var frames = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        renderRest("READY — tap START and speak A / E / I / O / U")
    }

    override fun onDestroy() {
        stopRecognition()
        super.onDestroy()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(36, 44, 36, 44)
            setBackgroundColor(Color.rgb(248, 249, 251))
        }

        root.addView(TextView(this).apply {
            text = "Mandarin Viseme MVP"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 26, 34))
        })
        root.addView(TextView(this).apply {
            text = "MIC → ~32 ms frame → spectral envelope → A/E/I/O/U\nLatency/UX PoC; not a production phoneme model."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(80, 88, 104))
            setPadding(0, 8, 0, 22)
        })

        visemeValue = TextView(this).apply {
            textSize = 56f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(20, 25, 34))
        }
        root.addView(visemeValue, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        imageView = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(8, 8, 8, 8)
        }
        root.addView(imageView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(330)
        ).apply { topMargin = dp(8) })

        metricsValue = TextView(this).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(35, 40, 52))
            setPadding(0, 18, 0, 8)
        }
        root.addView(metricsValue)

        statusValue = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(85, 92, 106))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 14)
        }
        root.addView(statusValue)

        toggleButton = Button(this).apply {
            text = "START REAL-TIME VISEME"
            setOnClickListener {
                if (running.get()) stopRecognition() else ensurePermissionAndStart()
            }
        }
        root.addView(toggleButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        root.addView(TextView(this).apply {
            text = "測試方式：依序持續發『啊、ㄜ/欸、衣、喔、嗚』約 1 秒。\n觀察上方 A/E/I/O/U 是否即時切換，以及 PROCESS 欄是否遠低於 32 ms。"
            textSize = 14f
            setTextColor(Color.rgb(70, 78, 92))
            setPadding(0, 22, 0, 0)
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun ensurePermissionAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecognition()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startRecognition()
        } else if (requestCode == permissionRequestCode) {
            renderRest("RECORD_AUDIO permission denied")
        }
    }

    private fun startRecognition() {
        if (!running.compareAndSet(false, true)) return
        classifier.reset()
        frames = 0
        startedAtMs = SystemClock.elapsedRealtime()
        toggleButton.text = "STOP"
        statusValue.text = "LISTENING — input: microphone / 16 kHz mono"

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            running.set(false)
            renderRest("AudioRecord buffer setup failed: $minBuffer")
            toggleButton.text = "START REAL-TIME VISEME"
            return
        }
        val bufferBytes = max(minBuffer, frameSamples * 2 * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes,
            ).also { recorder ->
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    throw IllegalStateException("AudioRecord not initialized")
                }
                recorder.startRecording()
            }
        } catch (error: Throwable) {
            running.set(false)
            audioRecord = null
            renderRest("AudioRecord start failed: ${error.javaClass.simpleName}")
            toggleButton.text = "START REAL-TIME VISEME"
            return
        }

        worker = Thread({ captureLoop() }, "VisemeMvpAudio").apply { start() }
    }

    private fun captureLoop() {
        val frame = ShortArray(frameSamples)
        while (running.get()) {
            val recorder = audioRecord ?: break
            val read = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
            if (read <= 0) continue
            val result = classifier.classify(frame, read)
            frames += 1
            val wallElapsedMs = SystemClock.elapsedRealtime() - startedAtMs
            runOnUiThread {
                if (running.get()) render(result, wallElapsedMs)
            }
        }
    }

    private fun stopRecognition() {
        if (!running.compareAndSet(true, false)) return
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        audioRecord?.release()
        audioRecord = null
        worker?.interrupt()
        worker = null
        toggleButton.text = "START REAL-TIME VISEME"
        statusValue.text = "STOPPED"
        imageView.alpha = 0.45f
    }

    private fun render(result: MandarinVisemeClassifier.Result, elapsedMs: Long) {
        val stable = result.viseme
        visemeValue.text = if (stable == MandarinVisemeClassifier.Viseme.REST) "REST" else stable.name

        if (stable != MandarinVisemeClassifier.Viseme.REST) {
            if (stable != lastUiViseme) loadVisemeImage(stable)
            imageView.alpha = 1.0f
            lastUiViseme = stable
        } else {
            imageView.alpha = 0.38f
        }

        val f1 = result.f1Hz?.let { "%.0f".format(it) } ?: "—"
        val f2 = result.f2Hz?.let { "%.0f".format(it) } ?: "—"
        val fps = if (elapsedMs > 0) frames * 1000.0 / elapsedMs else 0.0
        metricsValue.text = buildString {
            append("RAW      ${result.rawViseme.name}\n")
            append("RMS      ${"%.4f".format(result.rms)}\n")
            append("F1/F2    $f1 / $f2 Hz\n")
            append("CONF     ${"%.2f".format(result.confidence)}\n")
            append("PROCESS  ${"%.2f".format(result.processingMs)} ms\n")
            append("RATE     ${"%.1f".format(fps)} frames/s")
        }
        statusValue.text = if (stable == MandarinVisemeClassifier.Viseme.REST) {
            "SILENCE / below gate"
        } else {
            "LIVE — stable=${stable.name}, raw=${result.rawViseme.name}"
        }
    }

    private fun renderRest(message: String) {
        visemeValue.text = "REST"
        metricsValue.text = "RAW      —\nRMS      —\nF1/F2    —\nCONF     —\nPROCESS  —\nRATE     —"
        statusValue.text = message
        loadVisemeImage(MandarinVisemeClassifier.Viseme.A)
        imageView.alpha = 0.38f
        lastUiViseme = MandarinVisemeClassifier.Viseme.A
    }

    private fun loadVisemeImage(viseme: MandarinVisemeClassifier.Viseme) {
        val name = when (viseme) {
            MandarinVisemeClassifier.Viseme.A -> "viseme_a.jpg"
            MandarinVisemeClassifier.Viseme.E -> "viseme_e.jpg"
            MandarinVisemeClassifier.Viseme.I -> "viseme_i.jpg"
            MandarinVisemeClassifier.Viseme.O -> "viseme_o.jpg"
            MandarinVisemeClassifier.Viseme.U -> "viseme_u.jpg"
            MandarinVisemeClassifier.Viseme.REST -> return
        }
        assets.open("viseme_mvp/$name").use { input ->
            imageView.setImageBitmap(BitmapFactory.decodeStream(input))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
