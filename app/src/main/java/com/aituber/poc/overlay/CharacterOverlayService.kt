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
import android.view.View
import android.view.WindowManager
import com.aituber.poc.character.CharacterAdapterFactory
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.CharacterMode
import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.live2d.Live2DOverlayView
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
    private var live2dView: Live2DOverlayView? = null
    private var overlayView: View? = null
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
            MouthRenderDiagnostics.recordCharacterEngineRender()
            characterEngine?.bind(CaptureSessionState.current(), animationFrames[frameIndex % animationFrames.size])
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

        val overlaySelection = createOverlaySelection()
        val selection = overlaySelection.selection
        CharacterDiagnostics.configure(
            requestedMode = selection.requestedMode,
            activeAdapterId = selection.adapter.characterId,
            fallbackReason = selection.fallbackReason,
            live2dLifecycleState = overlaySelection.live2dLifecycleState
        )
        characterEngine = CharacterEngine(selection.adapter)
        windowManager = getSystemService(WindowManager::class.java)
        overlayView = overlaySelection.view
        windowManager?.addView(overlaySelection.view, overlayLayoutParams(overlaySelection.live2dActive))
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

    private fun overlayLayoutParams(live2dActive: Boolean): WindowManager.LayoutParams {
        val placement = if (live2dActive) {
            val metrics = resources.displayMetrics
            Live2DOverlayPlacement.compute(metrics.widthPixels, metrics.heightPixels)
        } else {
            null
        }
        if (placement != null) {
            CharacterDiagnostics.recordLive2DDisplayTransform(
                displayScale = placement.displayScale,
                offsetX = placement.offsetX,
                offsetY = placement.offsetY,
                viewportWidth = placement.width,
                viewportHeight = placement.height,
                bottomSafeZoneFraction = placement.bottomSafeZoneFraction,
                bottomSafeZonePx = placement.bottomSafeZonePx
            )
        }
        return WindowManager.LayoutParams(
            placement?.width ?: 180,
            placement?.height ?: 90,
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
            x = placement?.offsetX ?: 32
            y = placement?.offsetY ?: 220
        }
    }

    private fun createOverlaySelection(): OverlaySelection {
        if (requestedCharacterMode == CharacterMode.LIVE2D) {
            val live2d = Live2DOverlayView(this) { reason ->
                fallbackToMinimalMouthOnMain("LIVE2D_RUNTIME_FAILURE: $reason")
            }
            val live2dAdapter = Live2DCharacterAdapter(nativeView = live2d)
            if (live2dAdapter.available) {
                live2dView = live2d
                mouthView = null
                return OverlaySelection(
                    view = live2d,
                    live2dActive = true,
                    live2dLifecycleState = "WAITING_FOR_SURFACE",
                    selection = CharacterAdapterFactory.create(
                        requestedMode = CharacterMode.LIVE2D,
                        mouthView = fallbackMouthView(),
                        live2dAdapter = live2dAdapter
                    )
                )
            }
            live2d.release()
            OverlayLifecycleTrace.record("Live2D unavailable, fallback to minimal mouth")
        }

        val minimal = MouthOverlayView(this)
        mouthView = minimal
        live2dView = null
        return OverlaySelection(
            view = minimal,
            live2dActive = false,
            live2dLifecycleState = if (requestedCharacterMode == CharacterMode.LIVE2D) "FAILED" else "DISABLED",
            selection = CharacterAdapterFactory.create(
                requestedMode = requestedCharacterMode,
                mouthView = minimal,
                live2dAdapter = Live2DCharacterAdapter(sdkAvailable = false)
            )
        )
    }

    private fun fallbackMouthView(): MouthOverlayView {
        return mouthView ?: MouthOverlayView(this)
    }

    private fun fallbackToMinimalMouthOnMain(reason: String) {
        requireMainThread("fallbackToMinimalMouthOnMain")
        OverlayLifecycleTrace.record(reason)
        val oldView = overlayView
        live2dView?.release()
        oldView?.let { runCatching { windowManager?.removeView(it) } }

        val minimal = MouthOverlayView(this)
        mouthView = minimal
        live2dView = null
        overlayView = minimal
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.LIVE2D,
            mouthView = minimal,
            live2dAdapter = Live2DCharacterAdapter(sdkAvailable = false)
        )
        CharacterDiagnostics.configure(
            requestedMode = selection.requestedMode,
            activeAdapterId = selection.adapter.characterId,
            fallbackReason = reason,
            live2dLifecycleState = "FAILED"
        )
        characterEngine = CharacterEngine(selection.adapter)
        windowManager?.addView(minimal, overlayLayoutParams(live2dActive = false))
        renderSnapshotOnMain(CaptureSessionState.current())
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
        if (characterEngine == null) return
        currentState = snapshot.state
        applyMouthAmplitude(snapshot)
    }

    private fun cleanupOverlayOnMain() {
        requireMainThread("cleanupOverlayOnMain")
        stopAnimation()
        amplitudeMapper.reset()
        silenceGate.reset()
        MouthDriveDiagnostics.reset()
        MouthRenderDiagnostics.reset()
        CharacterDiagnostics.reset(requestedMode = requestedCharacterMode)
        live2dView?.release()
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        OverlayLifecycleTrace.record("overlay view removed")
        mouthView = null
        live2dView = null
        overlayView = null
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
                    MouthRenderDiagnostics.recordCharacterEngineRender()
                    characterEngine?.bind(snapshot, frame.smoothedOpen.toFloat())
                }
            }
            MouthDriveMode.PSEUDO_FALLBACK -> ensureAnimation()
            MouthDriveMode.CLOSED -> {
                stopAnimation(closeMouth = false)
                MouthRenderDiagnostics.recordCharacterEngineRender()
                characterEngine?.bind(snapshot, 0f)
            }
        }
    }

    companion object {
        const val USES_FOREGROUND_NOTIFICATION = false

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var requestedCharacterMode: CharacterMode = CharacterMode.MINIMAL_MOUTH

        @Volatile
        private var activeService: CharacterOverlayService? = null

        fun testMouthFullyOpenForDebug() {
            activeService?.runMouthFullyOpenTest()
        }
    }

    private fun runMouthFullyOpenTest() {
        handler.post {
            if (characterEngine == null) return@post
            val openStep = MouthDebugFullOpenTest.forcedOpenStep()
            stopAnimation(closeMouth = false)
            mouthView?.render(openStep.state)
            characterEngine?.bind(CaptureSessionState.current(), openStep.ratio ?: 0f)
            MouthRenderDiagnostics.recordMapper(MouthDriveMode.RMS, openStep.ratio?.toDouble(), openStep.ratio?.toDouble() ?: 0.0)
            handler.postDelayed({
                if (characterEngine == null) return@postDelayed
                val restoreStep = MouthDebugFullOpenTest.restoreStep(CaptureSessionState.current().state)
                mouthView?.render(restoreStep.state)
                renderSnapshotOnMain(CaptureSessionState.current())
            }, 2_000L)
        }
    }
}

private data class OverlaySelection(
    val view: View,
    val live2dActive: Boolean,
    val live2dLifecycleState: String,
    val selection: com.aituber.poc.character.CharacterAdapterSelection
)
