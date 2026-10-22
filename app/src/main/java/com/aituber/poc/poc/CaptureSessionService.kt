package com.aituber.poc.poc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import com.aituber.poc.R
import com.aituber.poc.aiadapter.AiAdapter
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.aiadapter.PlaybackCaptureAiAdapter
import com.aituber.poc.overlay.CharacterOverlayService
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

/**
 * Keeps the AI-speech detectors running while the user is in another app.
 *
 * Two ways to start:
 *  - [ACTION_START_LISTENING] (default): the speaker-output Visualizer and the voice-session
 *    probe only. Needs the microphone permission, no screen-recording consent.
 *  - [ACTION_START] (precise mode): additionally Audio Playback Capture of the AI apps, which
 *    needs a fresh MediaProjection consent every time.
 */
class CaptureSessionService : Service() {
    private val channelId = "aituber_capture_session"

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var adapter: AiAdapter? = null
    private var playbackProbe: AndroidPlaybackStateProbe? = null
    private var foregroundStarted = false
    private var listeningOnly = false
    private var targetLabel = AiTargetApps.GENERIC_LABEL
    private var stopping = false
    /** Nothing running (also before the first start), so stopping again publishes nothing. */
    private var stopped = true

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        CaptureStartupTrace.serviceOnCreate()
        playbackProbe = AndroidPlaybackStateProbe(this) { snapshot ->
            CaptureSessionState.updatePlaybackProbe(snapshot)
        }
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CaptureStartupTrace.serviceOnStartCommand(intent?.action)
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP -> stopCapture(CaptureStatus.STOPPED)
            ACTION_STOP_ALL -> {
                stopService(Intent(this, CharacterOverlayService::class.java))
                stopCapture(CaptureStatus.STOPPED)
            }
            ACTION_TOGGLE_CHARACTER -> {
                CharacterOverlayService.toggleCollapsed()
                // A tap on a stale notification must not leave an idle service behind.
                if (!foregroundStarted) stopSelf()
            }
            else -> CaptureStartupTrace.record("unexpected service action: ${intent?.action ?: "null"}")
        }
        // A MediaProjection consent token cannot be reused after the system kills us,
        // so a sticky restart would come back as a running service that captures nothing.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture(CaptureStatus.STOPPED)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startupSequence(foregroundType: Int) = CaptureSessionStartupSequence(
        object : CaptureSessionStartupSequence.Actions {
            override fun startForeground() {
                startForegroundCompat(foregroundType)
            }

            override fun recordCaptureTrace(step: String) {
                CaptureStartupTrace.record(step)
            }

            override fun recordVisualizerTrace(step: String) {
                VisualizerAudioProbe.recordStartupTrace(step)
            }

            override fun publish(status: String) {
                this@CaptureSessionService.publish(status)
            }

            override fun startPlaybackProbe() {
                playbackProbe?.start()
            }

            override fun startVisualizer() {
                VisualizerAudioProbe.startDetector()
            }
        }
    )

    /** Default mode: speaker output + voice-session probe, no consent dialog. */
    private fun startListening() {
        if (foregroundStarted) {
            CaptureStartupTrace.record("listening requested while already running")
            return
        }
        listeningOnly = true
        stopped = false
        startupSequence(foregroundTypeMicrophone()).run()
        if (!foregroundStarted) {
            stopCapture(CaptureStatus.SERVICE_START_FAILED)
            return
        }
        targetLabel = AiTargetApps.label(AiTargetApps.installed(this))
        CaptureStartupTrace.record("listening without Playback Capture; AI targets: $targetLabel")
        publish(CaptureStatus.LISTENING)
    }

    private fun startCapture(intent: Intent) {
        CaptureStartupTrace.startCaptureEntered()
        listeningOnly = false
        stopped = false
        startupSequence(foregroundTypeMediaProjection()).run()
        if (!foregroundStarted) {
            stopCapture(CaptureStatus.SERVICE_START_FAILED)
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == 0 || resultData == null) {
            CaptureStartupTrace.record("invalid result data")
            stopCapture(CaptureStatus.MEDIA_PROJECTION_DENIED)
            return
        }

        // Every installed AI app is captured. With none installed, Playback Capture is
        // skipped but the speaker-output Visualizer (already started above) still works.
        val targets = AiTargetApps.installed(this)
        targetLabel = AiTargetApps.label(targets)
        CaptureStartupTrace.record("AI targets: ${targets.joinToString { it.app.packageName }.ifEmpty { "none" }}")

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        CaptureStartupTrace.record("MediaProjection object created")
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture(CaptureStatus.MEDIA_PROJECTION_STOPPED)
            }
        }
        projection.registerCallback(callback, mainLooperHandler())

        mediaProjection = projection
        mediaProjectionCallback = callback

        adapter?.stop()
        adapter = PlaybackCaptureAiAdapter(
            context = this,
            mediaProjection = projection,
            targetUids = targets.map { it.uid },
            targetLabel = targetLabel
        )
        CaptureStartupTrace.record("PlaybackCaptureAiAdapter start requested")
        adapter?.start { snapshot -> CaptureSessionState.update(snapshot) }
    }

    private fun stopCapture(status: String) {
        if (stopping) return
        if (stopped) {
            // Already stopped (e.g. onDestroy after a failed start): keep the status that says why.
            stopSelf()
            return
        }
        stopping = true
        adapter?.stop()
        adapter = null
        VisualizerAudioProbe.stop()
        playbackProbe?.stop()
        mediaProjectionCallback?.let { callback ->
            runCatching { mediaProjection?.unregisterCallback(callback) }
        }
        runCatching { mediaProjection?.stop() }
        mediaProjectionCallback = null
        mediaProjection = null
        publish(status)
        if (foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foregroundStarted = false
        }
        stopped = true
        stopping = false
        stopSelf()
    }

    private fun publish(status: String) {
        CaptureSessionState.update(
            UniversalStateSnapshot(
                targetApp = targetLabel,
                detectionMethod = if (listeningOnly) {
                    DetectionMethod.SPEAKER_OUTPUT.label
                } else {
                    DetectionMethod.PLAYBACK_CAPTURE.label
                },
                state = UniversalAiState.UNKNOWN,
                audioLevel = null,
                captureStatus = status
            )
        )
    }

    private fun foregroundTypeMicrophone(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0

    private fun foregroundTypeMediaProjection(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0

    private fun startForegroundCompat(type: Int) {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type != 0) {
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } catch (exception: RuntimeException) {
            // e.g. the microphone permission was revoked between the check and this call.
            CaptureStartupTrace.record("startForeground failed: ${exception::class.java.simpleName}")
            Toast.makeText(applicationContext, "AITuber 偵測沒有啟動，請回到 AITuber 再按一次啟動", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val icon = Icon.createWithResource(this, R.drawable.ic_notification)
        builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AITuber 角色執行中")
            .setContentText("AI 開口時角色會跟著說話・點這裡回到 AITuber")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(icon, "收起／顯示角色", servicePendingIntent(ACTION_TOGGLE_CHARACTER, 1)).build())
            .addAction(Notification.Action.Builder(icon, "停止", servicePendingIntent(ACTION_STOP_ALL, 2)).build())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, CaptureSessionService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "AITuber capture session",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun mainLooperHandler() = android.os.Handler(mainLooper)

    companion object {
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.aituber.poc.action.START_CAPTURE"
        const val ACTION_START_LISTENING = "com.aituber.poc.action.START_LISTENING"
        const val ACTION_STOP = "com.aituber.poc.action.STOP_CAPTURE"
        /** Stop detection and the floating character (notification and overlay menu). */
        const val ACTION_STOP_ALL = "com.aituber.poc.action.STOP_ALL"
        /** Tuck the floating character away or bring it back (notification). */
        const val ACTION_TOGGLE_CHARACTER = "com.aituber.poc.action.TOGGLE_CHARACTER"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
