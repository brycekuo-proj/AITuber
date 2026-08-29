package com.aituber.poc.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.aituber.poc.viseme.MandarinVisemeClassifier
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI Voice -> output-mix waveform -> coarse Mandarin viseme MVP.
 *
 * Important: this activity does NOT open AudioRecord/microphone input. Android still requires
 * RECORD_AUDIO permission for Visualizer(0), which is used here to inspect the device output mix.
 * The overlay lets the tester switch to ChatGPT Voice and watch A/E/I/O/U/REST in real time.
 */
class VisemeMvpActivity : Activity() {
    private val permissionRequestCode = 2201
    private val running = AtomicBoolean(false)

    @Volatile
    private var classifier = MandarinVisemeClassifier(sampleRate = DEFAULT_SAMPLE_RATE)
    private var classifierSampleRate = DEFAULT_SAMPLE_RATE
    private var visualizer: Visualizer? = null
    private var startedAtMs = 0L
    private var frames = 0L
    private var lastUiViseme = MandarinVisemeClassifier.Viseme.REST

    private lateinit var visemeValue: TextView
    private lateinit var imageView: ImageView
    private lateinit var metricsValue: TextView
    private lateinit var statusValue: TextView
    private lateinit var toggleButton: Button
    private lateinit var openChatGptButton: Button

    private var windowManager: WindowManager? = null
    private var overlayRoot: LinearLayout? = null
    private var overlayLetter: TextView? = null
    private var overlayImage: ImageView? = null
    private var overlayMeta: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        renderRest("READY — source is Android OUTPUT MIX, not microphone")
    }

    override fun onResume() {
        super.onResume()
        if (!Settings.canDrawOverlays(this)) {
            statusValue.text = "Overlay permission is required to watch the result over ChatGPT Voice"
        }
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
            text = "AI Voice Viseme MVP v2"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(22, 26, 34))
        })
        root.addView(TextView(this).apply {
            text = "ChatGPT Voice → Android output mix → A/E/I/O/U/REST\nNO microphone AudioRecord is used."
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 78, 94))
            setPadding(0, 8, 0, 20)
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
            dp(300)
        ).apply { topMargin = dp(8) })

        metricsValue = TextView(this).apply {
            textSize = 15f
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
            text = "START AI VOICE VISEME"
            setOnClickListener {
                if (running.get()) stopRecognition() else ensurePermissionsAndStart()
            }
        }
        root.addView(toggleButton, fullWidthButtonParams())

        openChatGptButton = Button(this).apply {
            text = "OPEN CHATGPT"
            setOnClickListener { openChatGpt() }
        }
        root.addView(openChatGptButton, fullWidthButtonParams())

        root.addView(TextView(this).apply {
            text = "測試方式：\n1. 按 START AI VOICE VISEME。\n2. 看到右上角嘴型浮窗後按 OPEN CHATGPT。\n3. 進入 ChatGPT Voice，讓 AI 用中文連續回答。\n4. 看浮窗是否即時切換 A/E/I/O/U/REST。\n\nAndroid 仍會要求「麥克風」權限，原因是 Visualizer(0) API 的系統權限要求；本測試沒有建立麥克風 AudioRecord。"
            textSize = 14f
            setTextColor(Color.rgb(70, 78, 92))
            setPadding(0, 22, 0, 0)
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun fullWidthButtonParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }

    private fun ensurePermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionRequestCode)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            statusValue.text = "Grant overlay permission, then return and tap START again"
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return
        }
        startRecognition()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != permissionRequestCode) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            ensurePermissionsAndStart()
        } else {
            renderRest("RECORD_AUDIO permission denied — Visualizer(0) cannot start")
        }
    }

    private fun startRecognition() {
        if (!running.compareAndSet(false, true)) return
        frames = 0L
        startedAtMs = SystemClock.elapsedRealtime()
        classifier = MandarinVisemeClassifier(sampleRate = DEFAULT_SAMPLE_RATE)
        classifierSampleRate = DEFAULT_SAMPLE_RATE
        lastUiViseme = MandarinVisemeClassifier.Viseme.REST
        toggleButton.text = "STOP AI VOICE VISEME"
        statusValue.text = "STARTING OUTPUT MIX…"

        try {
            val captureRange = Visualizer.getCaptureSizeRange()
            val captureSize = 512.coerceIn(
                captureRange.getOrElse(0) { 256 },
                captureRange.getOrElse(1) { 1024 }
            )
            val captureRate = (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1)
            val effect = Visualizer(OUTPUT_MIX_SESSION).apply {
                setCaptureSize(captureSize)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            if (!running.get()) return
                            val bytes = waveform ?: return
                            val sampleRateHz = (samplingRate / 1000).coerceAtLeast(8_000)
                            if (sampleRateHz != classifierSampleRate) {
                                classifier = MandarinVisemeClassifier(sampleRate = sampleRateHz)
                                classifierSampleRate = sampleRateHz
                            }
                            val frameTimestampMs = SystemClock.elapsedRealtime()
                            val pcm = outputMixBytesToPcm16(bytes)
                            val result = classifier.classify(pcm, pcm.size)
                            frames += 1
                            val uiTimestampMs = SystemClock.elapsedRealtime()
                            val frameAgeMs = (uiTimestampMs - frameTimestampMs).coerceAtLeast(0L)
                            val elapsedMs = uiTimestampMs - startedAtMs
                            runOnUiThread {
                                if (running.get()) render(result, elapsedMs, frameAgeMs, sampleRateHz)
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) = Unit
                    },
                    captureRate,
                    true,
                    false,
                )
            }
            visualizer = effect
            showOverlay()
            effect.enabled = true
            statusValue.text = "OUTPUT MIX ACTIVE — switch to ChatGPT Voice now"
        } catch (error: Throwable) {
            running.set(false)
            releaseVisualizer()
            hideOverlay()
            toggleButton.text = "START AI VOICE VISEME"
            renderRest("OUTPUT MIX failed: ${error.javaClass.simpleName}: ${error.message ?: "n/a"}")
        }
    }

    private fun stopRecognition() {
        if (!running.compareAndSet(true, false)) {
            hideOverlay()
            releaseVisualizer()
            return
        }
        releaseVisualizer()
        hideOverlay()
        toggleButton.text = "START AI VOICE VISEME"
        statusValue.text = "STOPPED"
        imageView.alpha = 0.4f
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
    }

    private fun render(
        result: MandarinVisemeClassifier.Result,
        elapsedMs: Long,
        frameAgeMs: Long,
        sampleRateHz: Int,
    ) {
        val stable = result.viseme
        visemeValue.text = if (stable == MandarinVisemeClassifier.Viseme.REST) "REST" else stable.name

        if (stable != MandarinVisemeClassifier.Viseme.REST) {
            if (stable != lastUiViseme) loadVisemeImage(imageView, stable)
            imageView.alpha = 1.0f
            lastUiViseme = stable
        } else {
            imageView.alpha = 0.35f
        }

        val f1 = result.f1Hz?.let { "%.0f".format(it) } ?: "—"
        val f2 = result.f2Hz?.let { "%.0f".format(it) } ?: "—"
        val fps = if (elapsedMs > 0) frames * 1000.0 / elapsedMs else 0.0
        metricsValue.text = buildString {
            append("SOURCE   OUTPUT_MIX / Visualizer(0)\n")
            append("RAW      ${result.rawViseme.name}\n")
            append("RMS      ${"%.4f".format(result.rms)}\n")
            append("F1/F2    $f1 / $f2 Hz\n")
            append("CONF     ${"%.2f".format(result.confidence)}\n")
            append("PROCESS  ${"%.2f".format(result.processingMs)} ms\n")
            append("FRAMEAGE ${frameAgeMs} ms\n")
            append("RATE     ${"%.1f".format(fps)} callbacks/s\n")
            append("PCM      $sampleRateHz Hz")
        }
        statusValue.text = if (stable == MandarinVisemeClassifier.Viseme.REST) {
            if (result.rms < 0.005) "OUTPUT MIX: silence / no capturable waveform" else "OUTPUT MIX: audio present, classified REST"
        } else {
            "OUTPUT MIX LIVE — ${stable.name}"
        }
        updateOverlay(result, frameAgeMs)
    }

    private fun renderRest(message: String) {
        visemeValue.text = "REST"
        metricsValue.text = "SOURCE   OUTPUT_MIX\nRAW      —\nRMS      —\nF1/F2    —\nCONF     —\nPROCESS  —\nFRAMEAGE —\nRATE     —"
        statusValue.text = message
        loadVisemeImage(imageView, MandarinVisemeClassifier.Viseme.A)
        imageView.alpha = 0.35f
        lastUiViseme = MandarinVisemeClassifier.Viseme.A
    }

    private fun showOverlay() {
        if (overlayRoot != null || !Settings.canDrawOverlays(this)) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(Color.argb(225, 255, 255, 255))
        }
        val letter = TextView(this).apply {
            text = "AI VOICE: REST"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
        }
        val image = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.35f
        }
        val meta = TextView(this).apply {
            text = "OUTPUT MIX"
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        root.addView(letter, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(image, LinearLayout.LayoutParams(dp(120), dp(190)))
        root.addView(meta, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        loadVisemeImage(image, MandarinVisemeClassifier.Viseme.A)

        val params = WindowManager.LayoutParams(
            dp(150),
            dp(250),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(10)
            y = dp(100)
        }
        wm.addView(root, params)
        overlayRoot = root
        overlayLetter = letter
        overlayImage = image
        overlayMeta = meta
    }

    private fun updateOverlay(result: MandarinVisemeClassifier.Result, frameAgeMs: Long) {
        val stable = result.viseme
        overlayLetter?.text = "AI VOICE: ${stable.name}"
        overlayMeta?.text = "RMS ${"%.3f".format(result.rms)} | ${"%.1f".format(result.processingMs)}ms | age ${frameAgeMs}ms"
        overlayImage?.let { image ->
            if (stable == MandarinVisemeClassifier.Viseme.REST) {
                image.alpha = 0.3f
            } else {
                loadVisemeImage(image, stable)
                image.alpha = 1.0f
            }
        }
    }

    private fun hideOverlay() {
        val root = overlayRoot ?: return
        runCatching { windowManager?.removeView(root) }
        overlayRoot = null
        overlayLetter = null
        overlayImage = null
        overlayMeta = null
        windowManager = null
    }

    private fun loadVisemeImage(target: ImageView, viseme: MandarinVisemeClassifier.Viseme) {
        val name = when (viseme) {
            MandarinVisemeClassifier.Viseme.A -> "viseme_a.jpg"
            MandarinVisemeClassifier.Viseme.E -> "viseme_e.jpg"
            MandarinVisemeClassifier.Viseme.I -> "viseme_i.jpg"
            MandarinVisemeClassifier.Viseme.O -> "viseme_o.jpg"
            MandarinVisemeClassifier.Viseme.U -> "viseme_u.jpg"
            MandarinVisemeClassifier.Viseme.REST -> return
        }
        assets.open("viseme_mvp/$name").use { input ->
            target.setImageBitmap(BitmapFactory.decodeStream(input))
        }
    }

    private fun outputMixBytesToPcm16(data: ByteArray): ShortArray {
        return ShortArray(data.size) { index ->
            val unsigned = data[index].toInt() and 0xff
            ((unsigned - 128) shl 8).toShort()
        }
    }

    private fun openChatGpt() {
        val launch = packageManager.getLaunchIntentForPackage(CHATGPT_PACKAGE)
        if (launch == null) {
            statusValue.text = "ChatGPT app not installed"
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val OUTPUT_MIX_SESSION = 0
        private const val DEFAULT_SAMPLE_RATE = 44_100
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    }
}
