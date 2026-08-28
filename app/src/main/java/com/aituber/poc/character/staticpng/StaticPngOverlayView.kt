package com.aituber.poc.character.staticpng

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.aituber.poc.character.CharacterDiagnostics
import kotlin.random.Random

class StaticPngOverlayView(
    context: Context,
    private val characterPackage: StaticPngCharacterPackage = StaticPngCharacterPackage.XianxiaFemale
) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val contentView = FrameLayout(context)
    private val breathView = FrameLayout(context)
    private val imageView = ImageView(context)
    private val hairLayerView = ImageView(context)
    private val hairTransitionLayerView = ImageView(context)
    private val chestBreathLayerView = StaticPngChestBreathOverlayView(context)
    private val eyeLayerView = ImageView(context)
    private val mouthLayerView = ImageView(context)
    private val masterBitmap: Bitmap
    private val hairBitmaps: Map<StaticPngHairShape, Bitmap>
    private val eyeBitmaps: Map<StaticPngEyeShape, Bitmap>
    private val mouthBitmaps: Map<StaticPngMouthShape, Bitmap>
    private var activeHairShape = StaticPngHairShape.BASE
    private var activeShape = StaticPngMouthShape.CLOSED
    private var activeEyeShape = StaticPngEyeShape.OPEN
    private var activeRatio = 0f
    private var idleMotionEnabled = true
    private var idleMotionRunning = false
    private var idleMotionStartMs = 0L
    private var idleMotionFrame = StaticPngIdleMotion.frame(0L)
    private var breathMotionEnabled = true
    private var breathMotionRunning = false
    private var breathMotionStartMs = 0L
    private var breathMotionFrame = StaticPngBreathMotion.frame(0L)
    private var breathAmplitudePercent = StaticPngBreathMotion.amplitudePercent
    private var chestBreathMotionFrame = StaticPngChestBreathMotion.frame(0L)
    private var chestBreathAmplitudePercent = StaticPngChestBreathMotion.amplitudePercent
    private var breathPeriodMs = StaticPngBreathMotion.periodMs
    private var autoBlinkEnabled = true
    private var blinkRunning = false
    private var blinkScheduled = false
    private var nextBlinkAtMs: Long? = null
    private var blinkCount = 0L
    private var hairMotionEnabled = true
    private var hairMotionRunning = false
    private var hairTransitionMode = StaticPngHairTransitionMode.CROSSFADE
    private var crossfadeDurationMs = StaticPngRuntimeTuning.crossfadeMs
    private var hairSequenceIndex = 0
    private var nextHairTransitionAtMs: Long? = null
    private val hairTransitionRunnables = mutableListOf<Runnable>()
    private var lastHairTransitionFrom = StaticPngHairShape.BASE
    private var lastHairTransitionTo = StaticPngHairShape.BASE
    private var lastHairPipelineTriggered = false
    private var lastHairTransitionDurationMs = 0L
    private val idleMotionRunnable = object : Runnable {
        override fun run() {
            if (!idleMotionRunning) return
            applyIdleMotionFrame(SystemClock.uptimeMillis())
            postOnAnimation(this)
        }
    }
    private val breathMotionRunnable = object : Runnable {
        override fun run() {
            if (!breathMotionRunning) return
            applyBreathMotionFrame(SystemClock.uptimeMillis())
            postOnAnimation(this)
        }
    }
    private val blinkKeyframeRunnables = mutableListOf<Runnable>()
    private val autoBlinkRunnable = object : Runnable {
        override fun run() {
            blinkScheduled = false
            nextBlinkAtMs = null
            if (!autoBlinkEnabled || !isAttachedToWindow) {
                recordBlink()
                return
            }
            triggerBlink(scheduleNext = true)
        }
    }
    private val hairMotionRunnable = object : Runnable {
        override fun run() {
            if (!hairMotionRunning || !isAttachedToWindow) {
                recordHairLayer()
                return
            }
            hairSequenceIndex = if (hairSequenceIndex >= StaticPngHairMotion.SEQUENCE.lastIndex) {
                1
            } else {
                hairSequenceIndex + 1
            }
            transitionHairTo(StaticPngHairMotion.SEQUENCE[hairSequenceIndex].shape)
            scheduleNextHairTransition()
        }
    }

    init {
        visibility = View.VISIBLE
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        contentView.visibility = View.VISIBLE
        contentView.setBackgroundColor(Color.TRANSPARENT)
        contentView.clipChildren = false
        contentView.clipToPadding = false
        contentView.alpha = 1f
        addView(
            contentView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        breathView.visibility = View.VISIBLE
        breathView.setBackgroundColor(Color.TRANSPARENT)
        breathView.clipChildren = false
        breathView.clipToPadding = false
        contentView.addView(
            breathView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        imageView.visibility = View.VISIBLE
        imageView.setBackgroundColor(Color.TRANSPARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = false
        breathView.addView(
            imageView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        masterBitmap = context.assets.open(characterPackage.assetPath).use { input ->
            BitmapFactory.decodeStream(input)
        }
        imageView.setImageBitmap(masterBitmap)
        hairBitmaps = characterPackage.hairLayers.mapValues { (_, layer) ->
            context.assets.open(layer.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
        hairLayerView.visibility = View.GONE
        hairLayerView.background = null
        hairLayerView.imageTintList = null
        hairLayerView.clearColorFilter()
        hairLayerView.scaleType = ImageView.ScaleType.FIT_CENTER
        hairLayerView.adjustViewBounds = false
        hairLayerView.clipToOutline = false
        breathView.addView(
            hairLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        hairTransitionLayerView.visibility = View.GONE
        hairTransitionLayerView.background = null
        hairTransitionLayerView.imageTintList = null
        hairTransitionLayerView.clearColorFilter()
        hairTransitionLayerView.scaleType = ImageView.ScaleType.FIT_CENTER
        hairTransitionLayerView.adjustViewBounds = false
        hairTransitionLayerView.clipToOutline = false
        breathView.addView(
            hairTransitionLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        chestBreathLayerView.setSourceBitmap(masterBitmap)
        breathView.addView(
            chestBreathLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        eyeBitmaps = characterPackage.eyeLayers.mapValues { (_, layer) ->
            context.assets.open(layer.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
        eyeLayerView.visibility = View.GONE
        eyeLayerView.background = null
        eyeLayerView.imageTintList = null
        eyeLayerView.clearColorFilter()
        eyeLayerView.scaleType = ImageView.ScaleType.FIT_CENTER
        eyeLayerView.adjustViewBounds = false
        eyeLayerView.clipToOutline = false
        breathView.addView(
            eyeLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        mouthBitmaps = characterPackage.mouthLayers.mapValues { (_, layer) ->
            context.assets.open(layer.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
        mouthLayerView.visibility = View.GONE
        mouthLayerView.setBackgroundColor(Color.TRANSPARENT)
        mouthLayerView.scaleType = ImageView.ScaleType.FIT_CENTER
        mouthLayerView.adjustViewBounds = false
        mouthLayerView.clipToOutline = false
        breathView.addView(
            mouthLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        recordMouthShape()
        recordIdleMotion()
        recordBreathMotion()
        recordBlink()
        recordEyeLayer()
        recordHairLayer()
    }

    fun show() {
        visibility = View.VISIBLE
        updateHairLayer()
    }

    fun setMouthOpenRatio(ratio: Float) {
        activeRatio = ratio.coerceIn(0f, 1f)
        val nextShape = StaticPngMouthShape.fromRatio(activeRatio)
        if (nextShape != activeShape) {
            activeShape = nextShape
            updateMouthLayer()
        }
        recordMouthShape()
    }

    fun setMouthShapeForDebug(shape: StaticPngMouthShape) {
        activeShape = shape
        activeRatio = StaticPngMouthShape.debugRatio(shape)
        updateMouthLayer()
        recordMouthShape()
    }

    fun setIdleMotionEnabled(enabled: Boolean) {
        idleMotionEnabled = enabled
        if (enabled) {
            startIdleMotion()
        } else {
            stopIdleMotion(resetTransform = true)
        }
    }

    fun setBreathMotionEnabled(enabled: Boolean) {
        breathMotionEnabled = enabled
        if (enabled) {
            startBreathMotion()
        } else {
            stopBreathMotion(resetTransform = true)
        }
    }

    fun setBreathAmplitudePercent(amplitudePercent: Int) {
        StaticPngBreathMotion.setAmplitudePercent(amplitudePercent)
        breathAmplitudePercent = StaticPngBreathMotion.amplitudePercent
        if (breathMotionRunning) {
            val nowMs = SystemClock.uptimeMillis()
            // Keep legacy whole-body tuning visually inspectable, but chest breath is the primary effect.
            breathMotionStartMs = nowMs - breathPeriodMs / 2L
            applyBreathMotionFrame(nowMs)
        } else {
            recordBreathMotion()
        }
    }

    fun setChestBreathAmplitudePercent(amplitudePercent: Int) {
        StaticPngChestBreathMotion.setAmplitudePercent(amplitudePercent)
        chestBreathAmplitudePercent = StaticPngChestBreathMotion.amplitudePercent
        val nowMs = SystemClock.uptimeMillis()
        breathMotionStartMs = nowMs - breathPeriodMs / 2L
        applyBreathMotionFrame(nowMs, previewChest = true)
    }

    fun setBreathPeriodMs(periodMs: Long) {
        StaticPngBreathMotion.setPeriodMs(periodMs)
        breathPeriodMs = StaticPngBreathMotion.periodMs
        if (breathMotionRunning) applyBreathMotionFrame(SystemClock.uptimeMillis()) else recordBreathMotion()
    }

    fun triggerBlinkForDebug() {
        triggerBlink(scheduleNext = false)
    }

    fun setAutoBlinkEnabled(enabled: Boolean) {
        autoBlinkEnabled = enabled
        if (enabled) {
            scheduleNextBlink()
        } else {
            cancelBlinkCallbacks(resetShape = true)
        }
        recordBlink()
    }

    fun setEyeShapeForDebug(shape: StaticPngEyeShape) {
        cancelBlinkCallbacks(resetShape = false)
        activeEyeShape = shape
        updateEyeLayer()
        recordBlink()
        recordEyeLayer()
    }

    fun setHairShapeForDebug(shape: StaticPngHairShape) {
        stopHairMotion(resetShape = false)
        transitionHairTo(shape)
        recordHairLayer()
    }

    fun setHairMotionEnabled(enabled: Boolean) {
        hairMotionEnabled = enabled
        if (enabled) {
            startHairMotion()
        } else {
            stopHairMotion(resetShape = true)
        }
    }

    fun setHairTransitionMode(mode: StaticPngHairTransitionMode) {
        hairTransitionMode = mode
        recordHairLayer()
    }

    fun setCrossfadeDurationMs(durationMs: Long) {
        StaticPngRuntimeTuning.setCrossfadeMs(durationMs)
        crossfadeDurationMs = StaticPngRuntimeTuning.crossfadeMs
        recordHairLayer()
    }

    fun release() {
        stopHairMotion(resetShape = true)
        cancelBlinkCallbacks(resetShape = true)
        stopBreathMotion(resetTransform = true)
        stopIdleMotion(resetTransform = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (idleMotionEnabled) {
            startIdleMotion()
        }
        if (breathMotionEnabled) {
            startBreathMotion()
        }
        if (autoBlinkEnabled) {
            scheduleNextBlink()
        }
        if (hairMotionEnabled) {
            startHairMotion()
        }
    }

    override fun onDetachedFromWindow() {
        stopHairMotion(resetShape = true)
        cancelBlinkCallbacks(resetShape = true)
        stopBreathMotion(resetTransform = true)
        stopIdleMotion(resetTransform = true)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateBreathPivot(w, h)
        recordEyeLayer()
        recordHairLayer()
        recordBreathMotion()
    }

    private fun updateHairLayer() {
        applyHairShapeDirect(activeHairShape)
    }

    private fun transitionHairTo(shape: StaticPngHairShape) {
        if (shape == activeHairShape) {
            lastHairTransitionFrom = activeHairShape
            lastHairTransitionTo = shape
            lastHairPipelineTriggered = false
            lastHairTransitionDurationMs = 0L
            recordHairLayer()
            return
        }
        lastHairTransitionFrom = activeHairShape
        lastHairTransitionTo = shape
        lastHairPipelineTriggered = true
        lastHairTransitionDurationMs = when (hairTransitionMode) {
            StaticPngHairTransitionMode.DIRECT -> 0L
            StaticPngHairTransitionMode.CROSSFADE -> crossfadeDurationMs
            StaticPngHairTransitionMode.BRIDGE -> StaticPngHairMotion.BRIDGE_TO_BASE_MS +
                StaticPngHairMotion.BRIDGE_BASE_HOLD_MS +
                StaticPngHairMotion.BRIDGE_FROM_BASE_MS
        }
        when (hairTransitionMode) {
            StaticPngHairTransitionMode.DIRECT -> applyHairShapeDirect(shape)
            StaticPngHairTransitionMode.CROSSFADE -> crossfadeHairTo(shape)
            StaticPngHairTransitionMode.BRIDGE -> bridgeHairTo(shape)
        }
    }

    private fun applyHairShapeDirect(shape: StaticPngHairShape, cancelTransitions: Boolean = true) {
        if (cancelTransitions) {
            cancelHairTransitions()
        }
        activeHairShape = shape
        val bitmap = hairBitmapFor(shape)
        if (shape == StaticPngHairShape.BASE || bitmap == null) {
            hairLayerView.visibility = View.GONE
            hairLayerView.setImageDrawable(null)
            imageView.visibility = View.VISIBLE
            imageView.setImageBitmap(masterBitmap)
            chestBreathLayerView.setSourceBitmap(masterBitmap)
            hairTransitionLayerView.visibility = View.GONE
            hairTransitionLayerView.setImageDrawable(null)
            recordHairLayer()
            return
        }
        hairLayerView.setImageBitmap(bitmap)
        chestBreathLayerView.setSourceBitmap(bitmap)
        hairLayerView.alpha = 1f
        hairLayerView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        hairTransitionLayerView.visibility = View.GONE
        hairTransitionLayerView.setImageDrawable(null)
        recordHairLayer()
    }

    private fun crossfadeHairTo(shape: StaticPngHairShape) {
        cancelHairTransitions()
        val targetBitmap = hairBitmapFor(shape)
        if (targetBitmap == null) {
            applyHairShapeDirect(shape)
            return
        }
        hairTransitionLayerView.setImageBitmap(targetBitmap)
        hairTransitionLayerView.alpha = 0f
        hairTransitionLayerView.visibility = View.VISIBLE
        hairTransitionLayerView.animate()
            .alpha(1f)
            .setDuration(crossfadeDurationMs)
            .withEndAction {
                applyHairShapeDirect(shape)
            }
            .start()
        recordHairLayer()
    }

    private fun bridgeHairTo(shape: StaticPngHairShape) {
        cancelHairTransitions()
        val toBase = Runnable {
            applyHairShapeDirect(StaticPngHairShape.BASE, cancelTransitions = false)
        }
        val toTarget = Runnable {
            hairTransitionRunnables.clear()
            applyHairShapeDirect(shape)
        }
        hairTransitionRunnables += toBase
        hairTransitionRunnables += toTarget
        handler.postDelayed(toBase, StaticPngHairMotion.BRIDGE_TO_BASE_MS)
        handler.postDelayed(
            toTarget,
            StaticPngHairMotion.BRIDGE_TO_BASE_MS +
                StaticPngHairMotion.BRIDGE_BASE_HOLD_MS +
                StaticPngHairMotion.BRIDGE_FROM_BASE_MS
        )
        recordHairLayer()
    }

    private fun hairBitmapFor(shape: StaticPngHairShape): Bitmap? {
        return if (shape == StaticPngHairShape.BASE) masterBitmap else hairBitmaps[shape]
    }

    private fun cancelHairTransitions() {
        hairTransitionLayerView.animate().cancel()
        hairTransitionRunnables.forEach { handler.removeCallbacks(it) }
        hairTransitionRunnables.clear()
    }

    private fun updateMouthLayer() {
        val layer = characterPackage.mouthLayers[activeShape]
        val bitmap = mouthBitmaps[activeShape]
        if (layer == null || bitmap == null) {
            mouthLayerView.visibility = View.GONE
            mouthLayerView.setImageDrawable(null)
            return
        }
        mouthLayerView.setImageBitmap(bitmap)
        mouthLayerView.visibility = View.VISIBLE
    }

    private fun updateEyeLayer() {
        val layer = characterPackage.eyeLayers[activeEyeShape]
        val bitmap = eyeBitmaps[activeEyeShape]
        if (layer == null || bitmap == null) {
            eyeLayerView.visibility = View.GONE
            eyeLayerView.setImageDrawable(null)
            recordEyeLayer()
            return
        }
        eyeLayerView.setImageBitmap(bitmap)
        eyeLayerView.visibility = View.VISIBLE
        recordEyeLayer()
    }

    private fun recordMouthShape() {
        val patch = characterPackage.mouthReplacementPatch
        CharacterDiagnostics.recordStaticPngMouth(
            shape = activeShape.name,
            ratio = activeRatio,
            patchX = patch.x,
            patchY = patch.y,
            patchWidth = patch.width,
            patchHeight = patch.height
        )
    }

    private fun startIdleMotion() {
        if (idleMotionRunning || !isAttachedToWindow) {
            recordIdleMotion()
            return
        }
        idleMotionRunning = true
        idleMotionStartMs = SystemClock.uptimeMillis()
        applyIdleMotionFrame(idleMotionStartMs)
        postOnAnimation(idleMotionRunnable)
    }

    private fun stopIdleMotion(resetTransform: Boolean) {
        idleMotionRunning = false
        removeCallbacks(idleMotionRunnable)
        if (resetTransform) {
            idleMotionFrame = StaticPngIdleMotion.frame(0L)
            contentView.translationY = 0f
            contentView.scaleX = 1f
            contentView.scaleY = 1f
        }
        recordIdleMotion()
    }

    private fun applyIdleMotionFrame(nowMs: Long) {
        idleMotionFrame = StaticPngIdleMotion.frame(nowMs - idleMotionStartMs)
        contentView.translationY = idleMotionFrame.offsetYDp * resources.displayMetrics.density
        contentView.scaleX = idleMotionFrame.scale
        contentView.scaleY = idleMotionFrame.scale
        recordIdleMotion()
    }

    private fun recordIdleMotion() {
        CharacterDiagnostics.recordStaticPngIdleMotion(
            active = idleMotionRunning,
            phase = idleMotionFrame.phase,
            offsetYDp = idleMotionFrame.offsetYDp,
            scale = idleMotionFrame.scale
        )
    }

    private fun startBreathMotion() {
        if (breathMotionRunning || !isAttachedToWindow) {
            recordBreathMotion()
            return
        }
        breathMotionRunning = true
        breathMotionStartMs = SystemClock.uptimeMillis()
        applyBreathMotionFrame(breathMotionStartMs)
        postOnAnimation(breathMotionRunnable)
    }

    private fun stopBreathMotion(resetTransform: Boolean) {
        breathMotionRunning = false
        removeCallbacks(breathMotionRunnable)
        if (resetTransform) {
            breathMotionFrame = StaticPngBreathMotion.frame(
                elapsedMs = 0L,
                amplitudePercent = breathAmplitudePercent,
                periodMs = breathPeriodMs
            )
            chestBreathMotionFrame = StaticPngChestBreathMotion.frame(
                elapsedMs = 0L,
                amplitudePercent = chestBreathAmplitudePercent,
                periodMs = breathPeriodMs
            )
            breathView.scaleX = 1f
            breathView.scaleY = 1f
            chestBreathLayerView.setFrame(chestBreathMotionFrame, active = false)
        }
        recordBreathMotion()
    }

    private fun applyBreathMotionFrame(nowMs: Long, previewChest: Boolean = false) {
        val elapsedMs = nowMs - breathMotionStartMs
        breathMotionFrame = StaticPngBreathMotion.frame(
            elapsedMs = elapsedMs,
            amplitudePercent = breathAmplitudePercent,
            periodMs = breathPeriodMs
        )
        chestBreathMotionFrame = StaticPngChestBreathMotion.frame(
            elapsedMs = elapsedMs,
            amplitudePercent = chestBreathAmplitudePercent,
            periodMs = breathPeriodMs
        )
        breathView.scaleX = breathMotionFrame.scaleX
        breathView.scaleY = breathMotionFrame.scaleY
        chestBreathLayerView.setFrame(
            frame = chestBreathMotionFrame,
            active = breathMotionRunning || previewChest
        )
        recordBreathMotion()
    }

    private fun updateBreathPivot(viewWidth: Int, viewHeight: Int) {
        if (viewWidth <= 0 || viewHeight <= 0 || masterBitmap.width <= 0 || masterBitmap.height <= 0) return
        val fitScale = minOf(
            viewWidth.toFloat() / masterBitmap.width.toFloat(),
            viewHeight.toFloat() / masterBitmap.height.toFloat()
        )
        val renderedHeight = masterBitmap.height * fitScale
        val renderedTop = (viewHeight - renderedHeight) * 0.5f
        breathView.pivotX = viewWidth * 0.5f
        breathView.pivotY = renderedTop + renderedHeight
    }

    private fun recordBreathMotion() {
        CharacterDiagnostics.recordStaticPngBreathMotion(
            active = breathMotionRunning,
            phase = breathMotionFrame.phase,
            inhale = breathMotionFrame.inhale,
            scaleX = breathMotionFrame.scaleX,
            scaleY = breathMotionFrame.scaleY,
            amplitudePercent = breathAmplitudePercent,
            periodMs = breathPeriodMs,
            pivotX = breathView.pivotX,
            pivotY = breathView.pivotY,
            chestActive = breathMotionRunning && chestBreathAmplitudePercent > 0,
            chestAmplitudePercent = chestBreathAmplitudePercent,
            chestPhase = chestBreathMotionFrame.phase,
            chestInhale = chestBreathMotionFrame.inhale,
            chestSourceBounds = StaticPngChestBreathMotion.ROI.let { roi ->
                "${roi.x},${roi.y} ${roi.width}x${roi.height}"
            },
            chestSourceNormalizedBounds = StaticPngChestBreathMotion.normalizedRoi(
                characterPackage.sourceWidthPx,
                characterPackage.sourceHeightPx
            ),
            chestViewBounds = chestBreathLayerView.currentBaseBounds().compactString(),
            chestScaleX = chestBreathMotionFrame.scaleX,
            chestScaleY = chestBreathMotionFrame.scaleY,
            chestOffsetY = chestBreathLayerView.currentTransformedBounds().top -
                chestBreathLayerView.currentBaseBounds().top,
            chestLocalTransform = chestBreathMotionFrame.localTransformString()
        )
    }

    private fun startHairMotion() {
        if (hairMotionRunning || !isAttachedToWindow) {
            recordHairLayer()
            return
        }
        hairMotionRunning = true
        hairSequenceIndex = 0
        activeHairShape = StaticPngHairShape.BASE
        updateHairLayer()
        scheduleNextHairTransition()
    }

    private fun stopHairMotion(resetShape: Boolean) {
        hairMotionRunning = false
        nextHairTransitionAtMs = null
        handler.removeCallbacks(hairMotionRunnable)
        cancelHairTransitions()
        if (resetShape) {
            activeHairShape = StaticPngHairShape.BASE
            updateHairLayer()
        }
        recordHairLayer()
    }

    private fun scheduleNextHairTransition() {
        if (!hairMotionRunning || !hairMotionEnabled || !isAttachedToWindow) {
            recordHairLayer()
            return
        }
        nextHairTransitionAtMs = SystemClock.uptimeMillis() + StaticPngHairMotion.STEP_MS
        handler.removeCallbacks(hairMotionRunnable)
        handler.postDelayed(hairMotionRunnable, StaticPngHairMotion.STEP_MS)
        recordHairLayer()
    }

    private fun triggerBlink(scheduleNext: Boolean) {
        if (blinkRunning || !isAttachedToWindow) {
            if (scheduleNext && autoBlinkEnabled) scheduleNextBlink()
            recordBlink()
            return
        }
        blinkRunning = true
        StaticPngBlinkMotion.BLINK_SEQUENCE.forEach { keyframe ->
            lateinit var runnable: Runnable
            runnable = Runnable {
                blinkKeyframeRunnables.remove(runnable)
                if (!blinkRunning || !isAttachedToWindow) return@Runnable
                activeEyeShape = keyframe.shape
                updateEyeLayer()
                if (keyframe.delayMs == StaticPngBlinkMotion.BLINK_DURATION_MS) {
                    blinkRunning = false
                    blinkCount += 1L
                    if (scheduleNext && autoBlinkEnabled) {
                        if (StaticPngBlinkMotion.shouldDoubleBlink(Random.nextFloat())) {
                            scheduleDoubleBlink()
                        } else {
                            scheduleNextBlink()
                        }
                    }
                }
                recordBlink()
            }
            blinkKeyframeRunnables += runnable
            handler.postDelayed(runnable, keyframe.delayMs)
        }
        recordBlink()
    }

    private fun scheduleDoubleBlink() {
        if (blinkScheduled || blinkRunning || !autoBlinkEnabled || !isAttachedToWindow) {
            recordBlink()
            return
        }
        blinkScheduled = true
        nextBlinkAtMs = SystemClock.uptimeMillis() + StaticPngBlinkMotion.DOUBLE_BLINK_DELAY_MS
        handler.postDelayed(autoBlinkRunnable, StaticPngBlinkMotion.DOUBLE_BLINK_DELAY_MS)
        recordBlink()
    }

    private fun scheduleNextBlink() {
        if (blinkScheduled || blinkRunning || !autoBlinkEnabled || !isAttachedToWindow) {
            recordBlink()
            return
        }
        val delayMs = StaticPngBlinkMotion.intervalMs(Random.nextFloat())
        blinkScheduled = true
        nextBlinkAtMs = SystemClock.uptimeMillis() + delayMs
        handler.postDelayed(autoBlinkRunnable, delayMs)
        recordBlink()
    }

    private fun cancelBlinkCallbacks(resetShape: Boolean) {
        blinkScheduled = false
        blinkRunning = false
        nextBlinkAtMs = null
        handler.removeCallbacks(autoBlinkRunnable)
        blinkKeyframeRunnables.forEach { handler.removeCallbacks(it) }
        blinkKeyframeRunnables.clear()
        if (resetShape) {
            activeEyeShape = StaticPngEyeShape.OPEN
            updateEyeLayer()
        }
        recordBlink()
    }

    private fun recordBlink() {
        CharacterDiagnostics.recordStaticPngBlink(
            active = blinkRunning || blinkScheduled,
            eyeShape = activeEyeShape.name,
            autoEnabled = autoBlinkEnabled,
            nextBlinkInMs = nextBlinkAtMs?.let { (it - SystemClock.uptimeMillis()).coerceAtLeast(0L) },
            blinkCount = blinkCount
        )
        recordEyeLayer()
    }

    private fun recordEyeLayer() {
        val drawable = eyeLayerView.drawable
        CharacterDiagnostics.recordStaticPngEyeLayer(
            assetPath = characterPackage.eyeLayers[activeEyeShape]?.assetPath ?: "n/a",
            visible = eyeLayerView.visibility == View.VISIBLE,
            drawableWidth = drawable?.intrinsicWidth ?: 0,
            drawableHeight = drawable?.intrinsicHeight ?: 0,
            viewWidth = eyeLayerView.width,
            viewHeight = eyeLayerView.height,
            background = if (eyeLayerView.background == null) "null" else eyeLayerView.background.javaClass.simpleName,
            tint = if (eyeLayerView.imageTintList == null) "null" else "present",
            colorFilter = if (eyeLayerView.colorFilter == null) "null" else "present"
        )
    }

    private fun recordHairLayer() {
        val hairDrawable = hairLayerView.drawable
        val transitionDrawable = hairTransitionLayerView.drawable
        val masterDrawable = imageView.drawable
        val drawable = if (hairTransitionLayerView.visibility == View.VISIBLE) {
            transitionDrawable
        } else if (activeHairShape == StaticPngHairShape.BASE) {
            masterDrawable
        } else {
            hairDrawable
        }
        CharacterDiagnostics.recordStaticPngHairLayer(
            shape = activeHairShape.name,
            assetPath = characterPackage.hairLayers[activeHairShape]?.assetPath ?: characterPackage.assetPath,
            transitionMode = hairTransitionMode.name,
            transitionFrom = lastHairTransitionFrom.name,
            transitionTo = lastHairTransitionTo.name,
            transitionPipelineTriggered = lastHairPipelineTriggered,
            transitionDurationMs = lastHairTransitionDurationMs,
            visible = hairLayerView.visibility == View.VISIBLE || hairTransitionLayerView.visibility == View.VISIBLE,
            drawableWidth = drawable?.intrinsicWidth ?: 0,
            drawableHeight = drawable?.intrinsicHeight ?: 0,
            viewWidth = hairLayerView.width,
            viewHeight = hairLayerView.height,
            motionActive = hairMotionRunning,
            nextTransitionInMs = nextHairTransitionAtMs?.let { (it - SystemClock.uptimeMillis()).coerceAtLeast(0L) },
            background = if (hairLayerView.background == null) "null" else hairLayerView.background.javaClass.simpleName,
            tint = if (hairLayerView.imageTintList == null) "null" else "present",
            colorFilter = if (hairLayerView.colorFilter == null) "null" else "present"
        )
    }
}
