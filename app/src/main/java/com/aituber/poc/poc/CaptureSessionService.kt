package com.aituber.poc.poc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import com.aituber.poc.R
import com.aituber.poc.aiadapter.AiAdapter
import com.aituber.poc.aiadapter.CaptureStatus
import com.aituber.poc.aiadapter.PlaybackCaptureAiAdapter
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.DebugCharacterAdapter
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class CaptureSessionService : Service() {
    private val channelId = "aituber_capture_session"

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var adapter: AiAdapter? = null
    private var playbackProbe: AndroidPlaybackStateProbe? = null
    private lateinit var characterEngine: CharacterEngine
    private var foregroundStarted = false
    private var stopping = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        CaptureStartupTrace.serviceOnCreate()
        characterEngine = CharacterEngine(DebugCharacterAdapter { snapshot ->
            CaptureSessionState.update(snapshot)
        })
        playbackProbe = AndroidPlaybackStateProbe(this) { snapshot ->
            CaptureSessionState.updatePlaybackProbe(snapshot)
        }
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CaptureStartupTrace.serviceOnStartCommand(intent?.action)
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopCapture(CaptureStatus.STOPPED)
            else -> CaptureStartupTrace.record("unexpected service action: ${intent?.action ?: "null"}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopCapture(CaptureStatus.STOPPED)
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCapture(intent: Intent) {
        CaptureStartupTrace.startCaptureEntered()
        CaptureSessionStartupSequence(
            object : CaptureSessionStartupSequence.Actions {
                override fun startForeground() {
                    startForegroundCompat()
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
        ).run()

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == 0 || resultData == null) {
            CaptureStartupTrace.record("invalid result data")
            publish(CaptureStatus.MEDIA_PROJECTION_DENIED)
            stopSelf()
            return
        }

        val targetUid = ChatGptTarget.uid(this)
        if (targetUid == null) {
            CaptureStartupTrace.record("ChatGPT UID unavailable")
            publish(CaptureStatus.CHATGPT_NOT_INSTALLED)
            stopSelf()
            return
        }

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
            targetUid = targetUid,
            targetLabel = ChatGptTarget.label
        )
        CaptureStartupTrace.record("PlaybackCaptureAiAdapter start requested")
        adapter?.start { snapshot -> characterEngine.bind(snapshot) }
    }

    private fun stopCapture(status: String) {
        if (stopping) return
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
        stopping = false
        stopSelf()
    }

    private fun publish(status: String) {
        characterEngine.bind(
            UniversalStateSnapshot(
                targetApp = ChatGptTarget.label,
                detectionMethod = DetectionMethod.PLAYBACK_CAPTURE.label,
                state = UniversalAiState.UNKNOWN,
                audioLevel = null,
                captureStatus = status
            )
        )
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
            .setContentTitle("AITuber PoC capture")
            .setContentText("ChatGPT playback detection is active")
            .setOngoing(true)
            .build()
    }

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
        const val ACTION_STOP = "com.aituber.poc.action.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
