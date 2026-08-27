package com.aituber.poc.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.aituber.poc.character.CharacterAdapterFactory
import com.aituber.poc.character.CharacterDiagnostics
import com.aituber.poc.character.CharacterEngine
import com.aituber.poc.character.CharacterMode
import com.aituber.poc.character.live2d.Live2DCharacterAdapter
import com.aituber.poc.character.live2d.Live2DCharacterProfile
import com.aituber.poc.character.live2d.Live2DCharacterProfiles
import com.aituber.poc.character.live2d.Live2DOverlayView
import com.aituber.poc.character.live2d.Live2DProfileStore
import com.aituber.poc.character.staticpng.StaticPngCharacterAdapter
import com.aituber.poc.character.staticpng.StaticPngCharacterPackage
import com.aituber.poc.character.staticpng.StaticPngOverlayView
import com.aituber.poc.character.statevideo.StateVideoCharacterAdapter
import com.aituber.poc.character.statevideo.StateVideoCharacterPackageLoader
import com.aituber.poc.character.statevideo.StateVideoOverlayView
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
    private var stateVideoView: StateVideoOverlayView? = null
    private var staticPngView: StaticPngOverlayView? = null
    private var overlayView: View? = null
    private var characterEngine: CharacterEngine? = null
    private var windowManager: WindowManager? = null
    private var currentState = UniversalAiState.UNKNOWN
    private var animationRunning = false
    private var mouthDriveMode = MouthDriveMode.CLOSED
    private var dragState: Live2DDragState? = null
    private var live2dCurrentScale = Live2DOverlayScaleMath.DEFAULT_SCALE
    private var blinkTickRunning = false
    private var lastCharacterMouthOpen = 0f
    private var serviceCharacterMode: CharacterMode = CharacterMode.MINIMAL_MOUTH

    @Volatile
    private var stateVideoDebugState: UniversalAiState? = null

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

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (!blinkTickRunning || live2dView == null || characterEngine == null) return
            MouthRenderDiagnostics.recordCharacterEngineRender()
            characterEngine?.bind(CaptureSessionState.current(), lastCharacterMouthOpen)
            handler.postDelayed(this, BLINK_TICK_MS)
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
        serviceCharacterMode = requestedCharacterMode
        requestedLive2DProfileId = Live2DProfileStore.load(this).id
        CharacterDiagnostics.recordLive2DProfile(requestedLive2DProfile())
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
        val layoutParams = overlayLayoutParams(overlaySelection.live2dActive)
        if (overlaySelection.live2dActive) {
            installLive2DDrag(overlaySelection.view, layoutParams)
        }
        windowManager?.addView(overlaySelection.view, layoutParams)
        OverlayLifecycleTrace.record("overlay view attached")
        CaptureSessionState.subscribe(stateListener)
        OverlayLifecycleTrace.record("overlay subscribed to state")
        if (overlaySelection.live2dActive) {
            startBlinkTick()
        }
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
        stopBlinkTick()
        cleanupOverlayOnMain()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun overlayLayoutParams(live2dActive: Boolean): WindowManager.LayoutParams {
        val placement = if (live2dActive) {
            val metrics = resources.displayMetrics
            val positionStore = Live2DOverlayPositionStore(this)
            Live2DOverlayPlacement.compute(
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                systemTopInsetPx = systemTopInsetPx(),
                topInsetMarginPx = (8f * metrics.density).toInt(),
                density = metrics.density,
                requestedScale = positionStore.loadScale() ?: Live2DOverlayPlacement.DEFAULT_DISPLAY_SCALE
            )
        } else {
            null
        }
        if (placement != null) {
            live2dCurrentScale = placement.displayScale
        }
        val windowType = OverlayWindowConfig.windowType()
        val windowFlags = if (placement != null) {
            OverlayWindowConfig.live2dFlags()
        } else {
            OverlayWindowConfig.minimalMouthFlags()
        }
        val positionStore = if (placement != null) Live2DOverlayPositionStore(this) else null
        val position = if (placement != null) {
            val metrics = resources.displayMetrics
            Live2DOverlayDragMath.initialPosition(
                defaultPosition = Live2DOverlayPosition(placement.offsetX, placement.offsetY),
                savedPosition = positionStore?.load(),
                bounds = Live2DOverlayBounds(
                    screenWidth = metrics.widthPixels,
                    screenHeight = metrics.heightPixels,
                    overlayWidth = placement.width,
                    overlayHeight = placement.height
                )
            )
        } else {
            null
        }
        if (placement != null) {
            recordLive2DPlacementDiagnostics(
                placement = placement,
                position = position ?: Live2DOverlayPosition(placement.offsetX, placement.offsetY),
                windowType = windowType,
                windowFlags = windowFlags,
                dragging = false,
                positionSaved = positionStore?.isSaved() == true || positionStore?.isScaleSaved() == true
            )
        }
        return WindowManager.LayoutParams(
            placement?.width ?: 180,
            placement?.height ?: 90,
            windowType,
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (placement != null) {
                alpha = OverlayWindowConfig.LIVE2D_EXPERIMENTAL_WINDOW_ALPHA
            }
            gravity = if (placement != null) {
                Gravity.TOP or Gravity.START
            } else {
                Gravity.TOP or Gravity.END
            }
            x = position?.x ?: 32
            y = position?.y ?: 220
        }
    }

    private fun systemTopInsetPx(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun installLive2DDrag(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val positionStore = Live2DOverlayPositionStore(this)
        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    dragState = null
                    CharacterDiagnostics.recordLive2DDragState(
                        dragging = false,
                        x = params.x,
                        y = params.y,
                        positionSaved = positionStore.isSaved() || positionStore.isScaleSaved()
                    )
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val metrics = resources.displayMetrics
                    val range = Live2DOverlayScaleMath.scaleRange(metrics.heightPixels, metrics.density)
                    val nextScale = Live2DOverlayScaleMath.clampScale(
                        live2dCurrentScale * detector.scaleFactor,
                        range
                    )
                    if (kotlin.math.abs(nextScale - live2dCurrentScale) < 0.001f) return true

                    val currentSize = Live2DOverlayDimensions(params.width, params.height)
                    val nextSize = Live2DOverlayScaleMath.dimensionsForScale(nextScale)
                    val nextPosition = Live2DOverlayScaleMath.resizeAroundFocus(
                        currentPosition = Live2DOverlayPosition(params.x, params.y),
                        currentSize = currentSize,
                        nextSize = nextSize,
                        focusX = params.x + detector.focusX,
                        focusY = params.y + detector.focusY,
                        bounds = Live2DOverlayBounds(
                            screenWidth = metrics.widthPixels,
                            screenHeight = metrics.heightPixels,
                            overlayWidth = nextSize.width,
                            overlayHeight = nextSize.height
                        )
                    )

                    live2dCurrentScale = nextScale
                    params.width = nextSize.width
                    params.height = nextSize.height
                    params.x = nextPosition.x
                    params.y = nextPosition.y
                    windowManager?.updateViewLayout(view, params)
                    recordLive2DCurrentTransform(
                        params = params,
                        dragging = false,
                        positionSaved = positionStore.isSaved() || positionStore.isScaleSaved()
                    )
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    positionStore.save(Live2DOverlayPosition(params.x, params.y), live2dCurrentScale)
                    recordLive2DCurrentTransform(
                        params = params,
                        dragging = false,
                        positionSaved = true
                    )
                }
            }
        )
        view.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount > 1 || scaleDetector.isInProgress) {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    dragState = null
                }
                return@setOnTouchListener true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragState = Live2DDragState(
                        downRawX = event.rawX,
                        downRawY = event.rawY,
                        startX = params.x,
                        startY = params.y,
                        dragging = false
                    )
                    CharacterDiagnostics.recordLive2DDragState(
                        dragging = false,
                        x = params.x,
                        y = params.y,
                        positionSaved = positionStore.isSaved() || positionStore.isScaleSaved()
                    )
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val state = dragState ?: return@setOnTouchListener true
                    val deltaX = event.rawX - state.downRawX
                    val deltaY = event.rawY - state.downRawY
                    val movementExceededSlop = kotlin.math.abs(deltaX) >= touchSlop ||
                        kotlin.math.abs(deltaY) >= touchSlop
                    if (state.dragging || movementExceededSlop) {
                        dragState = state.copy(dragging = true)
                        val next = clampedLive2DPosition(
                            position = Live2DOverlayPosition(
                                x = state.startX + deltaX.toInt(),
                                y = state.startY + deltaY.toInt()
                            ),
                            overlayWidth = params.width,
                            overlayHeight = params.height
                        )
                        params.x = next.x
                        params.y = next.y
                        windowManager?.updateViewLayout(view, params)
                        CharacterDiagnostics.recordLive2DDragState(
                            dragging = true,
                            x = params.x,
                            y = params.y,
                            positionSaved = positionStore.isSaved() || positionStore.isScaleSaved()
                        )
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val finalPosition = clampedLive2DPosition(
                        position = Live2DOverlayPosition(params.x, params.y),
                        overlayWidth = params.width,
                        overlayHeight = params.height
                    )
                    params.x = finalPosition.x
                    params.y = finalPosition.y
                    if (dragState?.dragging == true) {
                        positionStore.save(finalPosition, live2dCurrentScale)
                    }
                    CharacterDiagnostics.recordLive2DDragState(
                        dragging = false,
                        x = params.x,
                        y = params.y,
                        positionSaved = positionStore.isSaved() || positionStore.isScaleSaved()
                    )
                    dragState = null
                    true
                }
                else -> true
            }
        }
    }

    private fun clampedLive2DPosition(
        position: Live2DOverlayPosition,
        overlayWidth: Int,
        overlayHeight: Int
    ): Live2DOverlayPosition {
        val metrics = resources.displayMetrics
        return Live2DOverlayDragMath.clamp(
            position = position,
            bounds = Live2DOverlayBounds(
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                overlayWidth = overlayWidth,
                overlayHeight = overlayHeight
            )
        )
    }

    private fun recordLive2DCurrentTransform(
        params: WindowManager.LayoutParams,
        dragging: Boolean,
        positionSaved: Boolean
    ) {
        val metrics = resources.displayMetrics
        val placement = Live2DOverlayPlacement.compute(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            systemTopInsetPx = systemTopInsetPx(),
            topInsetMarginPx = (8f * metrics.density).toInt(),
            density = metrics.density,
            requestedScale = live2dCurrentScale
        )
        recordLive2DPlacementDiagnostics(
            placement = placement,
            position = Live2DOverlayPosition(params.x, params.y),
            windowType = OverlayWindowConfig.windowType(),
            windowFlags = OverlayWindowConfig.live2dFlags(),
            dragging = dragging,
            positionSaved = positionSaved
        )
    }

    private fun recordLive2DPlacementDiagnostics(
        placement: Live2DOverlayPlacementFrame,
        position: Live2DOverlayPosition,
        windowType: Int,
        windowFlags: Int,
        dragging: Boolean,
        positionSaved: Boolean
    ) {
        CharacterDiagnostics.recordLive2DDisplayTransform(
            displayScale = placement.displayScale,
            minScale = placement.minScale,
            defaultScale = placement.defaultScale,
            maxScale = placement.maxScale,
            visibleHeightPercent = placement.visibleHeightPercent,
            offsetX = position.x,
            offsetY = position.y,
            viewportWidth = placement.width,
            viewportHeight = placement.height,
            anchor = placement.anchor,
            rightMarginFraction = placement.rightMarginFraction,
            rightMarginPx = placement.rightMarginPx,
            topSafeMarginFraction = placement.topSafeMarginFraction,
            topSafeMarginPx = placement.topSafeMarginPx,
            bottomSafeZoneFraction = placement.bottomSafeZoneFraction,
            bottomSafeZonePx = placement.bottomSafeZonePx,
            windowType = windowType,
            windowAlpha = OverlayWindowConfig.LIVE2D_EXPERIMENTAL_WINDOW_ALPHA,
            flagNotTouchable = OverlayWindowConfig.hasNotTouchable(windowFlags),
            flagNotFocusable = OverlayWindowConfig.hasNotFocusable(windowFlags),
            dragEnabled = true,
            dragging = dragging,
            windowTouchable = OverlayWindowConfig.isTouchable(windowFlags),
            positionSaved = positionSaved
        )
    }

    private fun createOverlaySelection(): OverlaySelection {
        if (serviceCharacterMode == CharacterMode.STATIC_PNG) {
            val staticPng = StaticPngOverlayView(this, StaticPngCharacterPackage.XianxiaFemale)
            staticPngView = staticPng
            live2dView = null
            stateVideoView = null
            mouthView = null
            return OverlaySelection(
                view = staticPng,
                live2dActive = true,
                live2dLifecycleState = "STATIC_PNG_ACTIVE",
                selection = CharacterAdapterFactory.create(
                    requestedMode = CharacterMode.STATIC_PNG,
                    mouthView = fallbackMouthView(),
                    staticPngAdapter = StaticPngCharacterAdapter(staticPng)
                )
            )
        }

        if (serviceCharacterMode == CharacterMode.STATE_VIDEO) {
            val stateVideoPackage = StateVideoCharacterPackageLoader.loadWhitehairFemale(this)
            val stateVideo = StateVideoOverlayView(this, stateVideoPackage)
            stateVideoView = stateVideo
            live2dView = null
            staticPngView = null
            mouthView = null
            return OverlaySelection(
                view = stateVideo,
                live2dActive = true,
                live2dLifecycleState = "STATE_VIDEO_ACTIVE",
                selection = CharacterAdapterFactory.create(
                    requestedMode = CharacterMode.STATE_VIDEO,
                    mouthView = fallbackMouthView(),
                    stateVideoAdapter = StateVideoCharacterAdapter(stateVideo, stateVideoPackage)
                )
            )
        }

        if (serviceCharacterMode == CharacterMode.LIVE2D) {
            val profile = requestedLive2DProfile()
            val live2d = Live2DOverlayView(this, profile = profile) { reason ->
                fallbackToMinimalMouthOnMain("LIVE2D_RUNTIME_FAILURE: $reason")
            }
            val live2dAdapter = Live2DCharacterAdapter(profile = profile, nativeView = live2d)
            if (live2dAdapter.available) {
                live2dView = live2d
                mouthView = null
                staticPngView = null
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
        val profile = requestedLive2DProfile()
        mouthView = minimal
        live2dView = null
        stateVideoView = null
        staticPngView = null
        return OverlaySelection(
            view = minimal,
            live2dActive = false,
            live2dLifecycleState = if (serviceCharacterMode == CharacterMode.LIVE2D) "FAILED" else "DISABLED",
            selection = CharacterAdapterFactory.create(
                requestedMode = serviceCharacterMode,
                mouthView = minimal,
                live2dAdapter = Live2DCharacterAdapter(profile = profile, sdkAvailable = false)
            )
        )
    }

    private fun fallbackMouthView(): MouthOverlayView {
        return mouthView ?: MouthOverlayView(this)
    }

    private fun fallbackToMinimalMouthOnMain(reason: String) {
        requireMainThread("fallbackToMinimalMouthOnMain")
        OverlayLifecycleTrace.record(reason)
        stopBlinkTick()
        val oldView = overlayView
        live2dView?.release()
        stateVideoView?.release()
        oldView?.let { runCatching { windowManager?.removeView(it) } }

        val minimal = MouthOverlayView(this)
        mouthView = minimal
        live2dView = null
        stateVideoView = null
        staticPngView = null
        overlayView = minimal
        val selection = CharacterAdapterFactory.create(
            requestedMode = CharacterMode.LIVE2D,
            mouthView = minimal,
            live2dAdapter = Live2DCharacterAdapter(profile = requestedLive2DProfile(), sdkAvailable = false)
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
        if (serviceCharacterMode == CharacterMode.STATE_VIDEO) {
            val debugState = stateVideoDebugState
            val effectiveSnapshot = if (debugState != null) {
                snapshot.copy(
                    state = debugState,
                    speakingSignalSource = "State video manual test"
                )
            } else {
                snapshot
            }
            currentState = effectiveSnapshot.state
            stopAnimation(closeMouth = false)
            MouthRenderDiagnostics.recordCharacterEngineRender()
            characterEngine?.bind(effectiveSnapshot, 0f)
            return
        }
        if (serviceCharacterMode == CharacterMode.STATIC_PNG) {
            renderStaticPngSnapshot(snapshot)
            return
        }
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
        live2dView?.release()
        stateVideoView?.release()
        stateVideoDebugState = null
        overlayView?.let { view -> runCatching { windowManager?.removeView(view) } }
        OverlayLifecycleTrace.record("overlay view removed")
        mouthView = null
        live2dView = null
        stateVideoView = null
        staticPngView = null
        overlayView = null
        characterEngine = null
        windowManager = null
        if (activeService === this) {
            CharacterDiagnostics.reset(requestedMode = serviceCharacterMode)
            isRunning = false
            activeService = null
            OverlayLifecycleTrace.setAlive(false)
        }
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
                    lastCharacterMouthOpen = frame.smoothedOpen.toFloat()
                    characterEngine?.bind(snapshot, frame.smoothedOpen.toFloat())
                }
            }
            MouthDriveMode.PSEUDO_FALLBACK -> ensureAnimation()
            MouthDriveMode.CLOSED -> {
                stopAnimation(closeMouth = false)
                MouthRenderDiagnostics.recordCharacterEngineRender()
                lastCharacterMouthOpen = 0f
                characterEngine?.bind(snapshot, 0f)
            }
        }
    }

    private fun renderStaticPngSnapshot(snapshot: UniversalStateSnapshot) {
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
        stopAnimation(closeMouth = false)
        currentState = snapshot.state
        lastCharacterMouthOpen = if (frame.mode == MouthDriveMode.RMS && frame.shouldRender) {
            frame.smoothedOpen.toFloat()
        } else {
            0f
        }
        MouthRenderDiagnostics.recordCharacterEngineRender()
        characterEngine?.bind(snapshot, lastCharacterMouthOpen)
    }

    private fun startBlinkTick() {
        requireMainThread("startBlinkTick")
        if (blinkTickRunning) return
        blinkTickRunning = true
        handler.post(blinkRunnable)
    }

    private fun stopBlinkTick() {
        requireMainThread("stopBlinkTick")
        blinkTickRunning = false
        characterEngine?.resetBlink()
        characterEngine?.resetBreath()
        handler.removeCallbacks(blinkRunnable)
    }

    companion object {
        const val USES_FOREGROUND_NOTIFICATION = false

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var requestedCharacterMode: CharacterMode = CharacterMode.MINIMAL_MOUTH

        @Volatile
        var requestedLive2DProfileId: String = Live2DCharacterProfiles.HARU_ID

        @Volatile
        private var activeService: CharacterOverlayService? = null

        fun requestedLive2DProfile(): Live2DCharacterProfile =
            Live2DCharacterProfiles.byId(requestedLive2DProfileId)

        fun testMouthFullyOpenForDebug() {
            activeService?.runMouthFullyOpenTest()
        }

        fun testBlinkForDebug() {
            activeService?.runBlinkTest()
        }

        fun testBreathForDebug() {
            activeService?.runBreathTest()
        }

        fun testIdleForDebug() {
            activeService?.runIdleTest()
        }

        fun testPhysicsForDebug() {
            activeService?.runPhysicsTest()
        }

        fun testStateVideoForDebug(state: UniversalAiState) {
            activeService?.runStateVideoTest(state)
            CaptureSessionState.forceStateForDebug(state, "State video manual test")
        }
    }

    private fun runMouthFullyOpenTest() {
        handler.post {
            if (characterEngine == null) return@post
            stopAnimation(closeMouth = false)
            MouthDebugSweep.RATIOS.forEachIndexed { index, ratio ->
                handler.postDelayed({
                    if (characterEngine == null) return@postDelayed
                    val state = if (ratio > 0f) UniversalAiState.SPEAKING else UniversalAiState.IDLE
                    mouthView?.render(state)
                    MouthRenderDiagnostics.recordMapper(MouthDriveMode.RMS, ratio.toDouble(), ratio.toDouble())
                    characterEngine?.bind(CaptureSessionState.current().copy(state = state), ratio)
                    if (index == MouthDebugSweep.RATIOS.lastIndex) {
                        renderSnapshotOnMain(CaptureSessionState.current())
                    }
                }, index * MouthDebugSweep.STEP_MS)
            }
        }
    }

    private fun runBlinkTest() {
        handler.post {
            characterEngine?.forceBlink()
            MouthRenderDiagnostics.recordCharacterEngineRender()
            characterEngine?.bind(CaptureSessionState.current(), lastCharacterMouthOpen)
        }
    }

    private fun runBreathTest() {
        handler.post {
            characterEngine?.forceTestBreath()
            MouthRenderDiagnostics.recordCharacterEngineRender()
            characterEngine?.bind(CaptureSessionState.current(), lastCharacterMouthOpen)
        }
    }

    private fun runIdleTest() {
        handler.post {
            live2dView?.startIdleMotionForDebug()
        }
    }

    private fun runPhysicsTest() {
        handler.post {
            if (requestedLive2DProfile().capabilities.idleMotion) {
                live2dView?.startPhysicsTestForDebug()
            } else {
                live2dView?.startEarTestForDebug()
                characterEngine?.forceTestBreath()
                MouthRenderDiagnostics.recordCharacterEngineRender()
                characterEngine?.bind(CaptureSessionState.current(), lastCharacterMouthOpen)
            }
        }
    }

    private fun runStateVideoTest(state: UniversalAiState) {
        if (serviceCharacterMode != CharacterMode.STATE_VIDEO) return
        stateVideoDebugState = state
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATE_VIDEO) return@post
            stopAnimation(closeMouth = false)
            val snapshot = CaptureSessionState.current().copy(
                state = state,
                speakingSignalSource = "State video manual test"
            )
            currentState = state
            stateVideoView?.renderManualTestState(state)
            MouthRenderDiagnostics.recordCharacterEngineRender()
            characterEngine?.bind(snapshot, 0f)
        }
    }
}

private const val BLINK_TICK_MS = 33L

private data class OverlaySelection(
    val view: View,
    val live2dActive: Boolean,
    val live2dLifecycleState: String,
    val selection: com.aituber.poc.character.CharacterAdapterSelection
)

private data class Live2DDragState(
    val downRawX: Float,
    val downRawY: Float,
    val startX: Int,
    val startY: Int,
    val dragging: Boolean
)
