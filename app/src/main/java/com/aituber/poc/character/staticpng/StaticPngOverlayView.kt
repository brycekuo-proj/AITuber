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
    private val imageView = ImageView(context)
    private val hairLayerView = ImageView(context)
    private val eyeLayerView = ImageView(context)
    private val mouthLayerView = ImageView(context)
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
    private var autoBlinkEnabled = true
    private var blinkRunning = false
    private var blinkScheduled = false
    private var nextBlinkAtMs: Long? = null
    private var blinkCount = 0L
    private var hairMotionEnabled = true
    private var hairMotionRunning = false
    private var hairSequenceIndex = 0
    private var nextHairTransitionAtMs: Long? = null
    private val idleMotionRunnable = object : Runnable {
        override fun run() {
            if (!idleMotionRunning) return
            applyIdleMotionFrame(SystemClock.uptimeMillis())
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
            activeHairShape = StaticPngHairMotion.SEQUENCE[hairSequenceIndex].shape
            updateHairLayer()
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
        addView(
            contentView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        imageView.visibility = View.VISIBLE
        imageView.setBackgroundColor(Color.TRANSPARENT)
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        imageView.adjustViewBounds = false
        contentView.addView(
            imageView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        context.assets.open(characterPackage.assetPath).use { input ->
            imageView.setImageBitmap(BitmapFactory.decodeStream(input))
        }
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
        contentView.addView(
            hairLayerView,
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
        contentView.addView(
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
        contentView.addView(
            mouthLayerView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        recordMouthShape()
        recordIdleMotion()
        recordBlink()
        recordEyeLayer()
        recordHairLayer()
    }

    fun show() {
        visibility = View.VISIBLE
        imageView.visibility = View.VISIBLE
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
        activeHairShape = shape
        updateHairLayer()
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

    fun release() {
        stopHairMotion(resetShape = true)
        cancelBlinkCallbacks(resetShape = true)
        stopIdleMotion(resetTransform = true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (idleMotionEnabled) {
            startIdleMotion()
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
        stopIdleMotion(resetTransform = true)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recordEyeLayer()
        recordHairLayer()
    }

    private fun updateHairLayer() {
        val layer = characterPackage.hairLayers[activeHairShape]
        val bitmap = hairBitmaps[activeHairShape]
        if (layer == null || bitmap == null) {
            hairLayerView.visibility = View.GONE
            hairLayerView.setImageDrawable(null)
            recordHairLayer()
            return
        }
        hairLayerView.setImageBitmap(bitmap)
        hairLayerView.visibility = View.VISIBLE
        recordHairLayer()
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
        val drawable = hairLayerView.drawable
        CharacterDiagnostics.recordStaticPngHairLayer(
            shape = activeHairShape.name,
            assetPath = characterPackage.hairLayers[activeHairShape]?.assetPath ?: "n/a",
            visible = hairLayerView.visibility == View.VISIBLE,
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
