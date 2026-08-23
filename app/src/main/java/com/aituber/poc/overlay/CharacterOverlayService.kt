package com.aituber.poc.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.MinimalMouthCharacterAdapter
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.state.UniversalAiState
import com.aituber.poc.state.UniversalStateSnapshot

class CharacterOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val animationFrames = floatArrayOf(0.08f, 0.35f, 0.85f, 0.45f, 0.12f, 0.70f, 1.00f, 0.25f)
    private val amplitudeMapper = MouthAmplitudeMapper()
    private val silenceGate = MouthSilenceGate()
    private var frameIndex = 0
    private var mouthView: MouthOverlayView? = null
    private var characterEngine: CharacterEngine? = null
    private var windowManager: WindowManager? = null
    private var currentState = UniversalAiState.UNKNOWN
    private var animationRunning = false
    private var mouthDriveMode = MouthDriveMode.CLOSED

    private val renderDispatcher = OverlayRenderDispatcher(
        postToMain = { block -> handler.post { block() } },
        postToMainDelayed = { block, delayMs -> handler.postDelayed({ block() }, delayMs) },
        nowMs = { SystemClock.elapsedRealtime() },
        isMainThread = { Looper.myLooper() == Looper.getMainLooper() },
        trace = OverlayLifecycleTrace::recordDeferred,
        renderOnMain = ::renderSnapshotOnMain,
        startAnimationOnMain = ::ensureAnimation,
        stopAnimationOnMain = ::stopAnimation
    )

    private val stateListener: (UniversalStateSnapshot) -> Unit = { snapshot ->
        renderDispatcher.onState(snapshot)
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!animationRunning || currentState != UniversalAiState.SPEAKING || mouthView == null) return
            mouthView?.setMouthOpenRatio(animationFrames[frameIndex % animationFrames.size])
            frameIndex += 1
            handler.postDelayed(this, 185L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        OverlayLifecycleTrace.record("CharacterOverlayService onCreate")
        activeService = this
        if (!Settings.canDrawOverlays(this)) {
            OverlayLifecycleTrace.record("overlay permission missing")
            activeService = null
            stopSelf()
            return
        }
        isRunning = true
        OverlayLifecycleTrace.setAlive(true)

        val view = MouthOverlayView(this)
        mouthView = view
        characterEngine = CharacterEngine(MinimalMouthCharacterAdapter(view))
        windowManager = getSystemService(WindowManager::class.java)
        windowManager?.addView(view, overlayLayoutParams())
        OverlayLifecycleTrace.record("overlay view attached")
        CaptureSessionState.subscribe(stateListener)
        OverlayLifecycleTrace.record("overlay subscribed to state")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        OverlayLifecycleTrace.record("CharacterOverlayService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        OverlayLifecycleTrace.record("CharacterOverlayService onDestroy")
        CaptureSessionState.unsubscribe(stateListener)
        OverlayLifecycleTrace.record("overlay unsubscribed")
        renderDispatcher.destroy()
        cleanupOverlayOnMain()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        180,
        90,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = 32
        y = 220
    }

    private fun ensureAnimation() {
        requireMainThread("ensureAnimation")
        if (animationRunning) return
        if (mouthDriveMode != MouthDriveMode.PSEUDO_FALLBACK) return
        animationRunning = true
        OverlayLifecycleTrace.recordDeferred("animation started")
        handler.post(animationRunnable)
    }

    private fun stopAnimation(closeMouth: Boolean = true) {
        requireMainThread("stopAnimation")
        val wasRunning = animationRunning
        animationRunning = false
        handler.removeCallbacks(animationRunnable)
        if (closeMouth) {
            mouthView?.setMouthOpenRatio(0f)
        }
        frameIndex = 0
        if (wasRunning) {
            OverlayLifecycleTrace.recordDeferred("animation stopped")
        }
    }

    private fun renderSnapshotOnMain(snapshot: UniversalStateSnapshot) {
        requireMainThread("renderSnapshotOnMain")
        if (mouthView == null) return
        currentState = snapshot.state
        MouthRenderDiagnostics.recordCharacterEngineRender()
        characterEngine?.bind(snapshot)
        applyMouthAmplitude(snapshot)
    }

    private fun cleanupOverlayOnMain() {
        requireMainThread("cleanupOverlayOnMain")
        stopAnimation()
        amplitudeMapper.reset()
        silenceGate.reset()
        MouthDriveDiagnostics.reset()
        MouthRenderDiagnostics.reset()
        mouthView?.let { view -> runCatching { windowManager?.removeView(view) } }
        OverlayLifecycleTrace.record("overlay view removed")
        mouthView = null
        characterEngine = null
        windowManager = null
        isRunning = false
        activeService = null
        OverlayLifecycleTrace.setAlive(false)
    }

    private fun requireMainThread(operation: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "$operation must run on the main thread"
        }
    }

    private fun applyMouthAmplitude(snapshot: UniversalStateSnapshot) {
        val nowMs = SystemClock.elapsedRealtime()
        val visualizerAvailable = snapshot.visualizerProbe.enabled == "YES" &&
            snapshot.visualizerProbe.waveformCallbackCount > 0L
        val gateFrame = silenceGate.evaluate(
            universalState = snapshot.state,
            metrics = snapshot.visualizerProbe.currentMetrics,
            visualizerAvailable = visualizerAvailable,
            nowMs = nowMs
        )
        val frame = amplitudeMapper.evaluate(
            state = snapshot.state,
            metrics = snapshot.visualizerProbe.currentMetrics,
            mouthActive = gateFrame.mouthActive,
            visualizerAvailable = visualizerAvailable,
            nowMs = nowMs
        )
        mouthDriveMode = frame.mode
        MouthDriveDiagnostics.update(frame, gateFrame)
        MouthRenderDiagnostics.recordMapper(frame.mode, frame.targetOpen, frame.smoothedOpen)
        MouthRenderDiagnostics.recordOverlaySnapshot(
            state = snapshot.state,
            metrics = snapshot.visualizerProbe.currentMetrics,
            targetOpen = frame.targetOpen,
            smoothedOpen = frame.smoothedOpen
        )
        when (frame.mode) {
            MouthDriveMode.RMS -> {
                stopAnimation(closeMouth = false)
                if (frame.shouldRender) {
                    mouthView?.setMouthOpenRatio(frame.smoothedOpen.toFloat())
                }
            }
            MouthDriveMode.PSEUDO_FALLBACK -> ensureAnimation()
            MouthDriveMode.CLOSED -> stopAnimation()
        }
    }

    companion object {
        const val USES_FOREGROUND_NOTIFICATION = false

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var activeService: CharacterOverlayService? = null

        fun testMouthFullyOpenForDebug() {
            activeService?.runMouthFullyOpenTest()
        }
    }

    private fun runMouthFullyOpenTest() {
        handler.post {
            if (mouthView == null) return@post
            val openStep = MouthDebugFullOpenTest.forcedOpenStep()
            stopAnimation(closeMouth = false)
            mouthView?.render(openStep.state)
            mouthView?.setMouthOpenRatio(openStep.ratio ?: 0f)
            MouthRenderDiagnostics.recordMapper(MouthDriveMode.RMS, openStep.ratio?.toDouble(), openStep.ratio?.toDouble() ?: 0.0)
            handler.postDelayed({
                if (mouthView == null) return@postDelayed
                val restoreStep = MouthDebugFullOpenTest.restoreStep(CaptureSessionState.current().state)
                mouthView?.render(restoreStep.state)
                renderSnapshotOnMain(CaptureSessionState.current())
            }, 2_000L)
        }
    }
}
