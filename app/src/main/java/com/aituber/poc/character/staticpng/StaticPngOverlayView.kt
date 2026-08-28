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
    private val eyeLayerView = ImageView(context)
    private val mouthLayerView = ImageView(context)
    private val eyeBitmaps: Map<StaticPngEyeShape, Bitmap>
    private val mouthBitmaps: Map<StaticPngMouthShape, Bitmap>
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
        eyeBitmaps = characterPackage.eyeLayers.mapValues { (_, layer) ->
            context.assets.open(layer.assetPath).use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
        eyeLayerView.visibility = View.GONE
        eyeLayerView.setBackgroundColor(Color.TRANSPARENT)
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

    fun release() {
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
    }

    override fun onDetachedFromWindow() {
        cancelBlinkCallbacks(resetShape = true)
        stopIdleMotion(resetTransform = true)
        super.onDetachedFromWindow()
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
            return
        }
        eyeLayerView.setImageBitmap(bitmap)
        eyeLayerView.visibility = View.VISIBLE
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
    }
}
