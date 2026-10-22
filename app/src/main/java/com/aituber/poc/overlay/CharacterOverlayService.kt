package com.aituber.poc.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.opengl.GLSurfaceView
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
import android.widget.Toast
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
import com.aituber.poc.character.staticpng.StaticPngEyeShape
import com.aituber.poc.character.staticpng.StaticPngHairShape
import com.aituber.poc.character.staticpng.StaticPngHairTransitionMode
import com.aituber.poc.character.staticpng.StaticPngMouthShape
import com.aituber.poc.character.staticpng.StaticPngThinkingFrameId
import com.aituber.poc.character.staticpng.StaticPngThinkingRuntimeTuning
import com.aituber.poc.character.staticpng.StaticPngThinkingTransitionMode
import com.aituber.poc.character.staticpng.StaticPngBreathMotion
import com.aituber.poc.character.staticpng.StaticPngRuntimeTuning
import com.aituber.poc.character.staticpng.StaticPngChestBreathMotion
import com.aituber.poc.character.staticpng.StaticPngOverlayView
import com.aituber.poc.character.live2d.Live2DOverlayPresence
import com.aituber.poc.poc.CaptureSessionService
import com.aituber.poc.poc.CaptureSessionState
import com.aituber.poc.settings.AituberSettings
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
    private var staticPngView: StaticPngOverlayView? = null
    private var overlayView: View? = null
    /** Layout of [overlayView], the character window. */
    private var characterParams: WindowManager.LayoutParams? = null
    /** Handle, menu and bubble: the only parts that take touches while the character lets them through. */
    private var controls: FloatingControls? = null
    /** Whether the character window can be moved and resized (not the minimal-mouth fallback). */
    private var characterMovable = false
    /** Move/resize mode: the character window takes touches until the user taps done. */
    private var adjusting = false
    /** Tucked into the bubble: the character window is invisible and not rendering. */
    private var collapsed = false
    private var handleDragStart: Live2DOverlayPosition? = null
    private val presencePolicy = AutoPresencePolicy()
    private val presenceRecheck = Runnable { evaluatePresence(CaptureSessionState.current()) }
    private val adjustTimeout = Runnable { setAdjusting(false) }
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
        characterParams = layoutParams
        characterMovable = overlaySelection.live2dActive
        staticPngView?.setDisplayScale(live2dCurrentScale)
        if (overlaySelection.live2dActive) {
            // Only reaches the character while it takes touches (move/resize mode).
            installLive2DDrag(overlaySelection.view, layoutParams)
        }
        windowManager?.addView(overlaySelection.view, layoutParams)
        attachFloatingControls(layoutParams)
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
        // Touches pass through to the AI app; the handle next to the character takes taps.
        val windowFlags = if (placement != null) {
            OverlayWindowConfig.passThroughFlags()
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
            alpha = OverlayWindowConfig.passThroughAlpha()
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

    private fun attachFloatingControls(characterParams: WindowManager.LayoutParams) {
        val wm = windowManager ?: return
        if (controls != null) return
        controls = FloatingControls(this, wm, controlsListener).also {
            it.attach(characterBounds(characterParams))
        }
        OverlayLifecycleTrace.record("overlay controls attached")
    }

    /** The character window in screen coordinates (its x is measured from the right with END gravity). */
    private fun characterBounds(params: WindowManager.LayoutParams): Rect {
        val metrics = resources.displayMetrics
        val left = if ((params.gravity and Gravity.END) == Gravity.END) {
            metrics.widthPixels - params.x - params.width
        } else {
            params.x
        }
        return Rect(left, params.y, left + params.width, params.y + params.height)
    }

    private fun updateControlsPosition(params: WindowManager.LayoutParams) {
        controls?.updateAnchor(characterBounds(params))
    }

    private val controlsListener = object : FloatingControls.Listener {
        override fun onHandleDragStart() {
            val params = characterParams ?: return
            if (!characterMovable) return
            handleDragStart = Live2DOverlayPosition(params.x, params.y)
        }

        override fun onHandleDrag(totalDx: Int, totalDy: Int) {
            val start = handleDragStart ?: return
            val view = overlayView ?: return
            val params = characterParams ?: return
            val next = clampedLive2DPosition(
                position = Live2DOverlayPosition(start.x + totalDx, start.y + totalDy),
                overlayWidth = params.width,
                overlayHeight = params.height
            )
            params.x = next.x
            params.y = next.y
            runCatching { windowManager?.updateViewLayout(view, params) }
            updateControlsPosition(params)
        }

        override fun onHandleDragEnd() {
            val params = characterParams
            if (handleDragStart != null && params != null) {
                Live2DOverlayPositionStore(this@CharacterOverlayService)
                    .save(Live2DOverlayPosition(params.x, params.y), live2dCurrentScale)
            }
            handleDragStart = null
        }

        override fun canAdjust(): Boolean = characterMovable

        override fun onAdjustRequested() = setAdjusting(true)

        override fun onAdjustDone() = setAdjusting(false)

        override fun onCollapseRequested() = setCollapsed(true, "menu")

        override fun onExpandRequested() {
            setCollapsed(false, "bubble")
            presencePolicy.onShown(SystemClock.elapsedRealtime(), conversationActive = false)
        }

        override fun onOpenAppRequested() = openAituberApp()

        override fun onStopRequested() = stopEverything()
    }

    /** Window alpha for the current mode; Android 12+ only lets touches through at 0.8 or less. */
    private fun currentWindowAlpha(): Float = when {
        collapsed -> 0f
        adjusting -> OverlayWindowConfig.overlayAlpha()
        else -> OverlayWindowConfig.passThroughAlpha()
    }

    private fun applyCharacterWindowState() {
        val view = overlayView ?: return
        val params = characterParams ?: return
        params.flags = if (adjusting && !collapsed) {
            OverlayWindowConfig.live2dFlags()
        } else {
            OverlayWindowConfig.passThroughFlags()
        }
        params.alpha = currentWindowAlpha()
        if (view.isAttachedToWindow) {
            runCatching { windowManager?.updateViewLayout(view, params) }
        }
    }

    /** Move/resize mode: the character takes touches (drag, pinch) until done or a timeout. */
    private fun setAdjusting(enabled: Boolean) {
        requireMainThread("setAdjusting")
        if (enabled == adjusting) return
        if (enabled && (!characterMovable || collapsed)) return
        adjusting = enabled
        handler.removeCallbacks(adjustTimeout)
        applyCharacterWindowState()
        controls?.setAdjusting(enabled)
        if (enabled) {
            restartAdjustTimeout()
            Toast.makeText(this, "拖曳角色移動、雙指縮放，好了按「✓ 完成」", Toast.LENGTH_LONG).show()
        }
        OverlayLifecycleTrace.record(if (enabled) "adjust mode on" else "adjust mode off")
    }

    private fun restartAdjustTimeout() {
        handler.removeCallbacks(adjustTimeout)
        if (adjusting) handler.postDelayed(adjustTimeout, ADJUST_TIMEOUT_MS)
    }

    /**
     * Tucked away: the character window stays attached (no model reload when it comes back)
     * but is fully transparent, lets touches through and stops drawing; the bubble shows instead.
     */
    private fun setCollapsed(value: Boolean, reason: String) {
        requireMainThread("setCollapsed")
        if (value == collapsed) return
        val params = characterParams ?: return
        if (value && adjusting) setAdjusting(false)
        collapsed = value
        applyCharacterWindowState()
        live2dView?.renderMode = if (value) GLSurfaceView.RENDERMODE_WHEN_DIRTY else GLSurfaceView.RENDERMODE_CONTINUOUSLY
        if (value) {
            stopBlinkTick()
        } else if (live2dView != null) {
            startBlinkTick()
        }
        controls?.setCollapsed(value, characterBounds(params))
        OverlayLifecycleTrace.record("character ${if (value) "tucked away" else "shown"} ($reason)")
    }

    private fun userToggleCollapsed() {
        if (overlayView == null) return
        if (collapsed) {
            setCollapsed(false, "notification")
            presencePolicy.onShown(SystemClock.elapsedRealtime(), conversationActive = false)
        } else {
            setCollapsed(true, "notification")
        }
    }

    /** Auto tuck-away / pop-up, see [AutoPresencePolicy]. */
    private fun evaluatePresence(snapshot: UniversalStateSnapshot) {
        if (overlayView == null || characterParams == null) return
        val now = SystemClock.elapsedRealtime()
        val chatActive = CaptureSessionState.directChatActive()
        val decision = presencePolicy.evaluate(
            nowMs = now,
            speaking = snapshot.state == UniversalAiState.SPEAKING,
            conversationActive = snapshot.playbackProbe.voiceSessionActive == "YES",
            chatActive = chatActive,
            collapsed = collapsed,
            autoHideEnabled = AituberSettings.autoHideCharacter(this),
            holdVisible = adjusting || handleDragStart != null || controls?.busy == true
        )
        when (decision.action) {
            AutoPresencePolicy.Action.EXPAND -> {
                setCollapsed(false, if (chatActive) "AITuber chat" else "AI speaking")
                presencePolicy.onShown(now, conversationActive = true)
            }
            AutoPresencePolicy.Action.COLLAPSE -> setCollapsed(true, "conversation idle")
            AutoPresencePolicy.Action.NONE -> Unit
        }
        handler.removeCallbacks(presenceRecheck)
        decision.recheckAtMs?.let { atMs ->
            handler.postDelayed(presenceRecheck, (atMs - now).coerceAtLeast(0L) + PRESENCE_RECHECK_SLACK_MS)
        }
    }

    private fun openAituberApp() {
        OverlayLifecycleTrace.record("open AITuber from the character menu")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        } ?: return
        runCatching { startActivity(launchIntent) }
    }

    /** Menu "停止": end detection and the floating character together. */
    private fun stopEverything() {
        requireMainThread("stopEverything")
        OverlayLifecycleTrace.record("stop requested from the character menu")
        if (CaptureSessionService.isRunning) {
            runCatching {
                startService(
                    Intent(this, CaptureSessionService::class.java).setAction(CaptureSessionService.ACTION_STOP)
                )
            }
        }
        cleanupOverlayOnMain()
        stopSelf()
        Toast.makeText(this, "AITuber 已停止", Toast.LENGTH_SHORT).show()
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
                    staticPngView?.setDisplayScale(nextScale)
                    params.width = nextSize.width
                    params.height = nextSize.height
                    params.x = nextPosition.x
                    params.y = nextPosition.y
                    windowManager?.updateViewLayout(view, params)
                    updateControlsPosition(params)
                    recordLive2DCurrentTransform(
                        params = params,
                        dragging = false,
                        positionSaved = positionStore.isSaved() || positionStore.isScaleSaved()
                    )
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    restartAdjustTimeout()
                    positionStore.save(Live2DOverlayPosition(params.x, params.y), live2dCurrentScale)
                    staticPngView?.setDisplayScale(live2dCurrentScale)
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
                        updateControlsPosition(params)
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
                    updateControlsPosition(params)
                    restartAdjustTimeout()
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
            windowAlpha = OverlayWindowConfig.overlayAlpha(),
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
                // The overlay now owns the Live2D runtime; the home preview steps aside.
                Live2DOverlayPresence.set(true)
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
        // A failure callback can arrive after this service was destroyed.
        if (windowManager == null || activeService !== this) return
        OverlayLifecycleTrace.record(reason)
        stopBlinkTick()
        val oldView = overlayView
        val wm = windowManager
        val live2d = live2dView
        staticPngView?.release()
        if (live2d != null) {
            live2d.release {
                oldView?.let { runCatching { wm?.removeView(it) } }
                Live2DOverlayPresence.set(false)
            }
        } else {
            oldView?.let { runCatching { wm?.removeView(it) } }
        }

        val minimal = MouthOverlayView(this)
        mouthView = minimal
        live2dView = null
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
        val fallbackParams = overlayLayoutParams(live2dActive = false)
        characterParams = fallbackParams
        // The minimal mouth cannot be moved or resized.
        characterMovable = false
        if (adjusting) {
            adjusting = false
            handler.removeCallbacks(adjustTimeout)
            controls?.setAdjusting(false)
        }
        if (collapsed) fallbackParams.alpha = 0f
        windowManager?.addView(minimal, fallbackParams)
        updateControlsPosition(fallbackParams)
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
        evaluatePresence(snapshot)
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
        staticPngView?.release()
        val wm = windowManager
        val rootView = overlayView
        val live2d = live2dView
        handler.removeCallbacks(presenceRecheck)
        handler.removeCallbacks(adjustTimeout)
        controls?.release()
        controls = null
        if (live2d != null) {
            // Detaching the window stops its GL thread; release the native runtime first,
            // and only then let the home preview take the runtime back.
            live2d.release {
                rootView?.let { view -> runCatching { wm?.removeView(view) } }
                Live2DOverlayPresence.set(false)
            }
        } else {
            rootView?.let { view -> runCatching { wm?.removeView(view) } }
        }
        OverlayLifecycleTrace.record("overlay view removed")
        mouthView = null
        live2dView = null
        staticPngView = null
        overlayView = null
        characterParams = null
        characterMovable = false
        adjusting = false
        collapsed = false
        handleDragStart = null
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
        // Detector-neutral loudness chosen by the state layer (Visualizer or Playback Capture).
        val signal = snapshot.mouthSignal
        val gateFrame = silenceGate.evaluate(
            universalState = snapshot.state,
            metrics = signal.metrics,
            visualizerAvailable = signal.available,
            nowMs = nowMs
        )
        val frame = amplitudeMapper.evaluate(
            state = snapshot.state,
            metrics = signal.metrics,
            mouthActive = gateFrame.mouthActive,
            visualizerAvailable = signal.available,
            nowMs = nowMs
        )
        mouthDriveMode = frame.mode
        MouthDriveDiagnostics.update(frame, gateFrame)
        MouthRenderDiagnostics.recordMapper(frame.mode, frame.targetOpen, frame.smoothedOpen)
        MouthRenderDiagnostics.recordOverlaySnapshot(
            state = snapshot.state,
            metrics = signal.metrics,
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
        // Detector-neutral loudness chosen by the state layer (Visualizer or Playback Capture).
        val signal = snapshot.mouthSignal
        val gateFrame = silenceGate.evaluate(
            universalState = snapshot.state,
            metrics = signal.metrics,
            visualizerAvailable = signal.available,
            nowMs = nowMs
        )
        val frame = amplitudeMapper.evaluate(
            state = snapshot.state,
            metrics = signal.metrics,
            mouthActive = gateFrame.mouthActive,
            visualizerAvailable = signal.available,
            nowMs = nowMs
        )
        mouthDriveMode = frame.mode
        MouthDriveDiagnostics.update(frame, gateFrame)
        MouthRenderDiagnostics.recordMapper(frame.mode, frame.targetOpen, frame.smoothedOpen)
        MouthRenderDiagnostics.recordOverlaySnapshot(
            state = snapshot.state,
            metrics = signal.metrics,
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
        var requestedLive2DProfileId: String = Live2DCharacterProfiles.TORORO_ID

        @Volatile
        private var activeService: CharacterOverlayService? = null

        fun requestedLive2DProfile(): Live2DCharacterProfile =
            Live2DCharacterProfiles.byId(requestedLive2DProfileId)

        /** Notification button: tuck the character into its bubble, or bring it back. */
        fun toggleCollapsed() {
            val service = activeService ?: return
            service.handler.post { service.userToggleCollapsed() }
        }

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


        fun testStaticPngMouthForDebug(shape: StaticPngMouthShape) {
            activeService?.runStaticPngMouthTest(shape)
        }

        fun setStaticPngIdleMotionForDebug(enabled: Boolean) {
            activeService?.runStaticPngIdleMotionTest(enabled)
        }

        fun setStaticPngBreathMotionForDebug(enabled: Boolean) {
            activeService?.runStaticPngBreathMotionTest(enabled)
        }

        fun setStaticPngBreathAmplitudeForDebug(amplitudePercent: Int) {
            StaticPngBreathMotion.setAmplitudePercent(amplitudePercent)
            activeService?.runStaticPngBreathAmplitudeTest(StaticPngBreathMotion.amplitudePercent)
        }

        fun setStaticPngChestBreathAmplitudeForDebug(amplitudePercent: Int) {
            StaticPngChestBreathMotion.setAmplitudePercent(amplitudePercent)
            activeService?.runStaticPngChestBreathAmplitudeTest(StaticPngChestBreathMotion.amplitudePercent)
        }

        fun setStaticPngBreathPeriodForDebug(periodMs: Long) {
            StaticPngBreathMotion.setPeriodMs(periodMs)
            activeService?.runStaticPngBreathPeriodTest(StaticPngBreathMotion.periodMs)
        }

        fun testStaticPngBlinkForDebug() {
            activeService?.runStaticPngBlinkTest()
        }

        fun setStaticPngAutoBlinkForDebug(enabled: Boolean) {
            activeService?.runStaticPngAutoBlinkTest(enabled)
        }

        fun testStaticPngEyeForDebug(shape: StaticPngEyeShape) {
            activeService?.runStaticPngEyeTest(shape)
        }

        fun testStaticPngHairForDebug(shape: StaticPngHairShape) {
            activeService?.runStaticPngHairTest(shape)
        }

        fun setStaticPngHairMotionForDebug(enabled: Boolean) {
            activeService?.runStaticPngHairMotionTest(enabled)
        }

        fun setStaticPngHairTransitionModeForDebug(mode: StaticPngHairTransitionMode) {
            activeService?.runStaticPngHairTransitionModeTest(mode)
        }

        fun setStaticPngCrossfadeDurationForDebug(durationMs: Long) {
            StaticPngRuntimeTuning.setCrossfadeMs(durationMs)
            activeService?.runStaticPngCrossfadeDurationTest(StaticPngRuntimeTuning.crossfadeMs)
        }

        fun testStaticPngThinkingFrameForDebug(frameId: StaticPngThinkingFrameId) {
            activeService?.runStaticPngThinkingFrameTest(frameId)
        }

        fun setStaticPngThinkingPlaybackForDebug(enabled: Boolean) {
            activeService?.runStaticPngThinkingPlaybackTest(enabled)
        }

        fun setStaticPngThinkingTransitionModeForDebug(mode: StaticPngThinkingTransitionMode) {
            activeService?.runStaticPngThinkingTransitionModeTest(mode)
        }

        fun setStaticPngThinkingFrameHoldDurationForDebug(durationMs: Long) {
            StaticPngThinkingRuntimeTuning.setFrameHoldMs(durationMs)
            activeService?.runStaticPngThinkingFrameHoldDurationTest(
                StaticPngThinkingRuntimeTuning.frameHoldMs
            )
        }

        fun setOverlayAlphaForDebug(alphaPercent: Int) {
            OverlayWindowConfig.setOverlayAlphaPercent(alphaPercent)
            activeService?.runOverlayAlphaTest(OverlayWindowConfig.overlayAlphaPercent)
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

    private fun runStaticPngMouthTest(shape: StaticPngMouthShape) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            stopAnimation(closeMouth = false)
            lastCharacterMouthOpen = StaticPngMouthShape.debugRatio(shape)
            staticPngView?.setMouthShapeForDebug(shape)
        }
    }

    private fun runStaticPngIdleMotionTest(enabled: Boolean) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setIdleMotionEnabled(enabled)
        }
    }

    private fun runStaticPngBreathMotionTest(enabled: Boolean) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setBreathMotionEnabled(enabled)
        }
    }

    private fun runStaticPngBreathAmplitudeTest(amplitudePercent: Int) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setBreathAmplitudePercent(amplitudePercent)
        }
    }

    private fun runStaticPngChestBreathAmplitudeTest(amplitudePercent: Int) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setChestBreathAmplitudePercent(amplitudePercent)
        }
    }

    private fun runStaticPngBreathPeriodTest(periodMs: Long) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setBreathPeriodMs(periodMs)
        }
    }

    private fun runStaticPngBlinkTest() {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.triggerBlinkForDebug()
        }
    }

    private fun runStaticPngAutoBlinkTest(enabled: Boolean) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setAutoBlinkEnabled(enabled)
        }
    }

    private fun runStaticPngEyeTest(shape: StaticPngEyeShape) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setEyeShapeForDebug(shape)
        }
    }

    private fun runStaticPngHairTest(shape: StaticPngHairShape) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setHairShapeForDebug(shape)
        }
    }

    private fun runStaticPngHairMotionTest(enabled: Boolean) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setHairMotionEnabled(enabled)
        }
    }

    private fun runStaticPngHairTransitionModeTest(mode: StaticPngHairTransitionMode) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setHairTransitionMode(mode)
        }
    }

    private fun runStaticPngCrossfadeDurationTest(durationMs: Long) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setCrossfadeDurationMs(durationMs)
        }
    }

    private fun runStaticPngThinkingFrameTest(frameId: StaticPngThinkingFrameId) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setThinkingFrameForDebug(frameId)
        }
    }

    private fun runStaticPngThinkingPlaybackTest(enabled: Boolean) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setThinkingPlaybackForDebug(enabled)
        }
    }

    private fun runStaticPngThinkingTransitionModeTest(mode: StaticPngThinkingTransitionMode) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setThinkingTransitionMode(mode)
        }
    }

    private fun runStaticPngThinkingFrameHoldDurationTest(durationMs: Long) {
        if (serviceCharacterMode != CharacterMode.STATIC_PNG) return
        handler.post {
            if (serviceCharacterMode != CharacterMode.STATIC_PNG) return@post
            staticPngView?.setThinkingFrameHoldDurationMs(durationMs)
        }
    }

    private fun runOverlayAlphaTest(alphaPercent: Int) {
        handler.post {
            OverlayWindowConfig.setOverlayAlphaPercent(alphaPercent)
            val view = overlayView ?: return@post
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return@post
            params.alpha = currentWindowAlpha()
            windowManager?.updateViewLayout(view, params)
        }
    }
}

private const val BLINK_TICK_MS = 33L

/** Move/resize mode ends by itself after this long without a touch, so the AI app is never left blocked. */
private const val ADJUST_TIMEOUT_MS = 20_000L

private const val PRESENCE_RECHECK_SLACK_MS = 50L

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
