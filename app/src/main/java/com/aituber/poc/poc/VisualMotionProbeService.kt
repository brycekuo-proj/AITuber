package com.aituber.poc.poc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import com.aituber.poc.R

class VisualMotionProbeService : Service() {
    private val channelId = "aituber_visual_motion_probe"
    private val analyzer = VisualMotionAnalyzer()
    private val accumulator = VisualMotionAccumulator()

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var foregroundStarted = false
    private var lastProcessedElapsedMs = 0L
    private var stopping = false
    private var automatedTestStartElapsedMs: Long? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProbe(intent, automatedTest = false)
            ACTION_START_30S_TEST -> startProbe(intent, automatedTest = true)
            ACTION_STOP -> stopProbe()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopProbe()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProbe(intent: Intent, automatedTest: Boolean) {
        startForegroundCompat()
        stopCaptureObjects()
        analyzer.reset()

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == 0 || resultData == null) {
            stopProbe()
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        val roi = analyzer.roiBounds(width, height)
        val now = SystemClock.elapsedRealtime()
        automatedTestStartElapsedMs = if (automatedTest) now else null
        accumulator.start(roi.toString(), automatedTest, now)
        CaptureSessionState.updateVisualMotion(accumulator.snapshot())

        handlerThread = HandlerThread("aituber-visual-motion").also { thread ->
            thread.start()
            handler = Handler(thread.looper)
        }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopProbe()
            }
        }
        projection.registerCallback(callback, handler)
        mediaProjection = projection
        projectionCallback = callback

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ source -> processLatestImage(source) }, handler)
            virtualDisplay = projection.createVirtualDisplay(
                "AITuberVisualMotionProbe",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
        }
    }

    private fun processLatestImage(source: ImageReader) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastProcessedElapsedMs < FRAME_INTERVAL_MS) {
            accumulator.skipFrame()
            CaptureSessionState.updateVisualMotion(accumulator.snapshot())
            source.acquireLatestImage()?.close()
            return
        }
        val image = source.acquireLatestImage() ?: return
        try {
            lastProcessedElapsedMs = now
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val processingStart = SystemClock.elapsedRealtime()
            val metrics = analyzer.metricsFromRgbaBytes(
                width = image.width,
                height = image.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                bytes = bytes
            )
            val processingMs = SystemClock.elapsedRealtime() - processingStart
            CaptureSessionState.updateVisualMotion(accumulator.record(now, metrics, processingMs))
            if (automatedTestStartElapsedMs?.let { start -> now - start >= TEST_DURATION_MS } == true) {
                stopProbe()
            }
        } finally {
            image.close()
        }
    }

    private fun stopProbe() {
        if (stopping) return
        stopping = true
        stopCaptureObjects()
        accumulator.stop()
        CaptureSessionState.updateVisualMotion(accumulator.snapshot())
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foregroundStarted = false
        }
        stopping = false
        stopSelf()
    }

    private fun stopCaptureObjects() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projectionCallback?.let { callback ->
            runCatching { mediaProjection?.unregisterCallback(callback) }
        }
        projectionCallback = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        lastProcessedElapsedMs = 0L
        automatedTestStartElapsedMs = null
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AITuber visual motion probe")
            .setContentText("Center ROI motion probe is active")
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "AITuber visual motion probe",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.aituber.poc.action.START_VISUAL_MOTION"
        const val ACTION_START_30S_TEST = "com.aituber.poc.action.START_30S_VISUAL_TEST"
        const val ACTION_STOP = "com.aituber.poc.action.STOP_VISUAL_MOTION"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val NOTIFICATION_ID = 3001
        private const val FRAME_INTERVAL_MS = 125L
        private const val TEST_DURATION_MS = 30_000L
    }
}
